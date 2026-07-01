package com.studyplatform.controller;

import com.studyplatform.dto.job.JobResponse;
import com.studyplatform.dto.recommendation.RecommendationResponse;
import com.studyplatform.entity.AsyncJob;
import com.studyplatform.enums.JobType;
import com.studyplatform.security.CurrentUser;
import com.studyplatform.security.UserPrincipal;
import com.studyplatform.service.AsyncJobRunner;
import com.studyplatform.service.JobService;
import com.studyplatform.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final JobService jobService;
    private final AsyncJobRunner asyncJobRunner;

    @GetMapping
    public ResponseEntity<List<RecommendationResponse>> getRecommendations(
            @CurrentUser UserPrincipal principal) {
        return ResponseEntity.ok(recommendationService.getRecommendations(principal.getId()));
    }

    /** Kicks off recommendation regeneration in the background and returns the job. */
    @PostMapping("/generate")
    public ResponseEntity<JobResponse> triggerGeneration(
            @CurrentUser UserPrincipal principal) {
        AsyncJob job = jobService.create(principal.getUser(),
                JobType.RECOMMENDATIONS, "Mise à jour des recommandations");
        asyncJobRunner.runRecommendations(job.getId(), principal.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobService.toResponse(job));
    }

    @PostMapping("/{recommendationId}/acted")
    public ResponseEntity<Void> markActedOn(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID recommendationId) {
        recommendationService.markActedOn(recommendationId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
