package com.studyplatform.service;

import com.studyplatform.dto.auth.*;
import com.studyplatform.dto.group.JoinGroupRequest;
import com.studyplatform.entity.User;
import com.studyplatform.enums.RegistrationMode;
import com.studyplatform.exception.ApiException;
import com.studyplatform.repository.UserRepository;
import com.studyplatform.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    private final GroupService groupService;
    private final OtpService otpService;

    /**
     * Create the (still unverified) account and email an OTP. No JWT tokens
     * are issued until the email is confirmed via {@link #verifyOtp}.
     */
    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        // An account is created up-front (still unverified) so the OTP can be
        // tied to it. If the user abandoned a previous attempt the record is
        // still there but unverified — we reuse it (overwriting the pending
        // details) rather than blocking. Only a fully verified account is a
        // real conflict.
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null && user.isEmailVerified()) {
            throw ApiException.conflict("An account with this email already exists");
        }
        if (user == null) {
            user = new User();
            user.setEmail(email);
        } else {
            log.info("Overwriting abandoned (unverified) registration for {}", email);
        }

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setAccountType(request.getAccountType());
        user.setEmailVerified(false);
        user.setEducationLevel(request.getEducationLevel());
        user.setRegistrationMode(request.getRegistrationMode());
        user.setPreferenceDomains(
                request.getPreferenceDomains() != null
                        ? String.join(",", request.getPreferenceDomains())
                        : null);
        user.setMonitoredFolder(request.getMonitoredFolder());
        user.setObjectives(request.getObjectives());

        user = userRepository.save(user);
        log.info("New user registered (pending verification): {} ({})", user.getEmail(), user.getAccountType());

        otpService.generateAndSend(user);

        return RegistrationResponse.builder()
                .email(user.getEmail())
                .accountType(user.getAccountType())
                .requiresVerification(true)
                .message("A verification code has been sent to your email")
                .build();
    }

    /**
     * Confirm the email with the OTP code. Only now do we auto-join any group
     * and issue JWT tokens.
     */
    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        User user = otpService.verify(request.getEmail(), request.getCode());

        // Auto-join group if an invite code was carried over from registration
        if (user.getRegistrationMode() == RegistrationMode.JOIN_GROUP
                && request.getGroupInviteCode() != null
                && !request.getGroupInviteCode().isBlank()) {
            try {
                JoinGroupRequest joinRequest = new JoinGroupRequest();
                joinRequest.setInviteCode(request.getGroupInviteCode());
                groupService.join(user, joinRequest);
                log.info("User {} auto-joined group via invite code", user.getEmail());
            } catch (Exception e) {
                log.warn("Auto-join failed for user {}: {}", user.getEmail(), e.getMessage());
                // Don't fail verification if group join fails
            }
        }

        return buildAuthResponse(user);
    }

    /** Re-send a fresh OTP code to an unverified account. */
    @Transactional
    public void resendOtp(ResendOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> ApiException.notFound("No account found with this email"));
        if (user.isEmailVerified()) {
            throw ApiException.badRequest("This email is already verified");
        }
        otpService.generateAndSend(user);
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail().toLowerCase().trim(),
                            request.getPassword()));
        } catch (BadCredentialsException e) {
            throw ApiException.unauthorized("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));

        if (!user.isEmailVerified()) {
            throw ApiException.forbidden("Please verify your email before signing in");
        }

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!tokenProvider.validateToken(refreshToken)) {
            throw ApiException.unauthorized("Invalid or expired refresh token");
        }

        if (!"refresh".equals(tokenProvider.getTokenType(refreshToken))) {
            throw ApiException.unauthorized("Token is not a refresh token");
        }

        String email = tokenProvider.getEmailFromToken(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.unauthorized("User not found"));

        return buildAuthResponse(user);
    }

    public void requestPasswordReset(PasswordResetRequest request) {
        // For now, just verify the email exists.
        // In production: generate a reset token, store it with expiry,
        // and send an email with a reset link.
        userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> ApiException.notFound("No account found with this email"));

        log.info("Password reset requested for: {}", request.getEmail());
        // TODO: Implement email sending with reset token
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .accountType(user.getAccountType())
                .build();
    }
}
