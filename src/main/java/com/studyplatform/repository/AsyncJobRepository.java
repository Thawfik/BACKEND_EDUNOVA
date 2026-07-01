package com.studyplatform.repository;

import com.studyplatform.entity.AsyncJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AsyncJobRepository extends JpaRepository<AsyncJob, UUID> {
    List<AsyncJob> findTop30ByUserIdOrderByCreatedAtDesc(UUID userId);
}