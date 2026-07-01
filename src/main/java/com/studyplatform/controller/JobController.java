package com.studyplatform.controller;

import com.studyplatform.dto.job.JobResponse;
import com.studyplatform.security.CurrentUser;
import com.studyplatform.security.UserPrincipal;
import com.studyplatform.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    /** Recent jobs for the current user — used to rebuild the task tray on reload. */
    @GetMapping("/mine")
    public ResponseEntity<List<JobResponse>> listMine(@CurrentUser UserPrincipal principal) {
        return ResponseEntity.ok(jobService.listByUser(principal.getId()));
    }

    /** Polling fallback when the WebSocket push was missed. */
    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getById(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID jobId) {
        return ResponseEntity.ok(jobService.getById(jobId, principal.getId()));
    }
}