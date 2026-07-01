package com.studyplatform.dto.auth;

import com.studyplatform.enums.AccountType;
import lombok.Builder;
import lombok.Data;

/**
 * Returned by /api/auth/register. No JWT tokens are issued yet — the user
 * must first confirm their email via OTP (/api/auth/verify-otp).
 */
@Data
@Builder
public class RegistrationResponse {
    private String email;
    private AccountType accountType;
    @Builder.Default
    private boolean requiresVerification = true;
    private String message;
}
