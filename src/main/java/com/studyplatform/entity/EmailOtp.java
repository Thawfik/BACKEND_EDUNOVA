package com.studyplatform.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One-time passcode sent to a user's email to confirm ownership during
 * registration. The plaintext code is never stored — only its BCrypt hash.
 */
@Entity
@Table(name = "email_otps", indexes = @Index(name = "idx_email_otp_email", columnList = "email"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmailOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean consumed = false;

    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
