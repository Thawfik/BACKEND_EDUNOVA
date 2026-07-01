package com.studyplatform.service;

import com.studyplatform.entity.EmailOtp;
import com.studyplatform.entity.User;
import com.studyplatform.exception.ApiException;
import com.studyplatform.repository.EmailOtpRepository;
import com.studyplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Generates, sends and verifies the 6-digit email OTP used to confirm a
 * user's email address during registration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailOtpRepository otpRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.otp.expiry-minutes:10}")
    private int expiryMinutes;

    /** Generate a fresh code for the user, invalidating any previous one, and email it. */
    @Transactional
    public void generateAndSend(User user) {
        String email = user.getEmail();
        otpRepository.deleteByEmail(email);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        EmailOtp otp = EmailOtp.builder()
                .email(email)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES))
                .build();
        otpRepository.save(otp);

        emailService.sendOtpEmail(email, user.getFirstName(), code);
        log.info("OTP generated for {}", email);
    }

    /**
     * Verify a code for an email. On success the matching user is marked
     * emailVerified and the user is returned.
     */
    @Transactional
    public User verify(String email, String code) {
        String normalized = email.toLowerCase().trim();

        EmailOtp otp = otpRepository
                .findTopByEmailAndConsumedFalseOrderByCreatedAtDesc(normalized)
                .orElseThrow(() -> ApiException.badRequest("No verification code found. Please request a new one."));

        if (otp.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Verification code has expired. Please request a new one.");
        }
        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            throw ApiException.badRequest("Too many attempts. Please request a new code.");
        }

        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            throw ApiException.badRequest("Invalid verification code.");
        }

        otp.setConsumed(true);
        otpRepository.save(otp);

        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        user.setEmailVerified(true);
        userRepository.save(user);

        log.info("Email verified for {}", normalized);
        return user;
    }
}
