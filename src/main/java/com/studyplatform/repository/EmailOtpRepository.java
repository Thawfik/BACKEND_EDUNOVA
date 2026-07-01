package com.studyplatform.repository;

import com.studyplatform.entity.EmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, UUID> {

    Optional<EmailOtp> findTopByEmailAndConsumedFalseOrderByCreatedAtDesc(String email);

    void deleteByEmail(String email);
}
