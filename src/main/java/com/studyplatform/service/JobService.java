package com.studyplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.dto.job.JobResponse;
import com.studyplatform.entity.AsyncJob;
import com.studyplatform.entity.User;
import com.studyplatform.enums.JobStatus;
import com.studyplatform.enums.JobType;
import com.studyplatform.exception.ApiException;
import com.studyplatform.repository.AsyncJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Central bookkeeping for background jobs. Every state change is persisted and
 * pushed to the owning user over WebSocket ({@code /topic/jobs/{userId}}) so the
 * frontend task tray updates live without polling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final AsyncJobRepository jobRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public AsyncJob create(User user, JobType type, String title) {
        AsyncJob job = AsyncJob.builder()
                .user(user)
                .type(type)
                .status(JobStatus.PENDING)
                .title(title)
                .build();
        job = jobRepository.save(job);
        broadcast(job);
        return job;
    }

    @Transactional
    public void markRunning(UUID jobId) {
        AsyncJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        job.setStatus(JobStatus.RUNNING);
        broadcast(jobRepository.save(job));
    }

    @Transactional
    public void markCompleted(UUID jobId, UUID resultId, String resultType, JsonNode payload) {
        AsyncJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        job.setStatus(JobStatus.COMPLETED);
        job.setResultId(resultId);
        job.setResultType(resultType);
        job.setResultPayload(payload != null ? payload.toString() : null);
        job.setCompletedAt(Instant.now());
        broadcast(jobRepository.save(job));
    }

    @Transactional
    public void markFailed(UUID jobId, String errorMessage) {
        AsyncJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        job.setStatus(JobStatus.FAILED);
        job.setErrorMessage(errorMessage != null && errorMessage.length() > 1900
                ? errorMessage.substring(0, 1900) : errorMessage);
        job.setCompletedAt(Instant.now());
        broadcast(jobRepository.save(job));
    }

    public List<JobResponse> listByUser(UUID userId) {
        return jobRepository.findTop30ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public JobResponse getById(UUID jobId, UUID userId) {
        AsyncJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("Job not found"));
        if (!job.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("You do not have access to this job");
        }
        return toResponse(job);
    }

    private void broadcast(AsyncJob job) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/jobs/" + job.getUser().getId(), toResponse(job));
        } catch (Exception e) {
            log.warn("Failed to broadcast job {}: {}", job.getId(), e.getMessage());
        }
    }

    public JobResponse toResponse(AsyncJob job) {
        JsonNode payload = null;
        if (job.getResultPayload() != null) {
            try { payload = objectMapper.readTree(job.getResultPayload()); }
            catch (Exception ignored) {}
        }
        return JobResponse.builder()
                .id(job.getId())
                .type(job.getType().name())
                .status(job.getStatus().name())
                .title(job.getTitle())
                .resultId(job.getResultId())
                .resultType(job.getResultType())
                .resultPayload(payload)
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }
}