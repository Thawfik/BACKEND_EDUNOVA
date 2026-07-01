package com.studyplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.dto.guide.GenerateGuideRequest;
import com.studyplatform.dto.guide.GuideResponse;
import com.studyplatform.dto.quiz.GenerateQuizRequest;
import com.studyplatform.dto.quiz.QuizResponse;
import com.studyplatform.entity.User;
import com.studyplatform.exception.ApiException;
import com.studyplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Executes long-running work off the request thread. Each method is the
 * background half of a fire-and-forget endpoint: it re-loads the user, runs the
 * existing (synchronous) service logic, and reports progress through
 * {@link JobService}, which pushes updates to the UI over WebSocket.
 *
 * <p>This lives in its own bean so the {@code @Async} proxy actually applies —
 * a self-invocation from inside the domain service would run inline.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AsyncJobRunner {

    private final UserRepository userRepository;
    private final JobService jobService;
    private final GuideService guideService;
    private final QuizService quizService;
    private final RecommendationService recommendationService;
    private final ObjectMapper objectMapper;

    @Async
    public void runGuideGeneration(UUID jobId, UUID userId, GenerateGuideRequest request) {
        run(jobId, () -> {
            User user = loadUser(userId);
            GuideResponse guide = guideService.generate(user, request);
            jobService.markCompleted(jobId, guide.getId(), "guide", null);
        });
    }

    @Async
    public void runQuizGeneration(UUID jobId, UUID userId, GenerateQuizRequest request) {
        run(jobId, () -> {
            User user = loadUser(userId);
            QuizResponse quiz = quizService.generate(user, request);
            jobService.markCompleted(jobId, quiz.getId(), "quiz", null);
        });
    }

    @Async
    public void runQuizFromGuide(UUID jobId, UUID userId, UUID guideId) {
        run(jobId, () -> {
            User user = loadUser(userId);
            QuizResponse quiz = quizService.generateFromGuide(user, guideId);
            jobService.markCompleted(jobId, quiz.getId(), "quiz", null);
        });
    }

    @Async
    public void runGuideTranslation(UUID jobId, UUID userId, UUID guideId, String lang) {
        run(jobId, () -> {
            GuideResponse translated = guideService.translate(guideId, userId, lang);
            JsonNode payload = objectMapper.valueToTree(translated);
            jobService.markCompleted(jobId, guideId, "guide-translation", payload);
        });
    }

    @Async
    public void runRecommendations(UUID jobId, UUID userId) {
        run(jobId, () -> {
            User user = loadUser(userId);
            recommendationService.regenerate(user);
            jobService.markCompleted(jobId, null, "recommendations", null);
        });
    }

    /** Shared lifecycle: mark running, execute, and convert any failure into a FAILED job. */
    private void run(UUID jobId, Runnable work) {
        jobService.markRunning(jobId);
        try {
            work.run();
        } catch (Exception e) {
            log.error("Async job {} failed: {}", jobId, e.getMessage(), e);
            jobService.markFailed(jobId, e.getMessage());
        }
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }
}