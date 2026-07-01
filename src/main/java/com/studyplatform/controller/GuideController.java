package com.studyplatform.controller;

import com.studyplatform.dto.guide.GenerateGuideRequest;
import com.studyplatform.dto.guide.GuideListResponse;
import com.studyplatform.dto.guide.GuideResponse;
import com.studyplatform.dto.job.JobResponse;
import com.studyplatform.entity.AsyncJob;
import com.studyplatform.entity.StudyGuide;
import com.studyplatform.enums.JobType;
import com.studyplatform.export.PdfExportService;
import com.studyplatform.security.CurrentUser;
import com.studyplatform.security.UserPrincipal;
import com.studyplatform.service.AsyncJobRunner;
import com.studyplatform.service.GuideService;
import com.studyplatform.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/guides")
@RequiredArgsConstructor
public class GuideController {

    private final GuideService guideService;
    private final PdfExportService pdfExportService;
    private final JobService jobService;
    private final AsyncJobRunner asyncJobRunner;

    /** Kicks off guide generation in the background and returns the job to track. */
    @PostMapping("/generate")
    public ResponseEntity<JobResponse> generate(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody GenerateGuideRequest request) {
        AsyncJob job = jobService.create(principal.getUser(),
                JobType.GUIDE_GENERATION, "Génération du guide : " + request.getTopic());
        asyncJobRunner.runGuideGeneration(job.getId(), principal.getId(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobService.toResponse(job));
    }

    @GetMapping
    public ResponseEntity<List<GuideListResponse>> listMine(@CurrentUser UserPrincipal principal) {
        return ResponseEntity.ok(guideService.listByUser(principal.getId()));
    }

    @GetMapping("/{guideId}")
    public ResponseEntity<GuideResponse> getById(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID guideId) {
        return ResponseEntity.ok(guideService.getById(guideId, principal.getId()));
    }

    /** Kicks off translation in the background; the translated guide arrives via the job. */
    @PostMapping("/{guideId}/translate")
    public ResponseEntity<JobResponse> translate(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID guideId,
            @RequestParam(value = "lang", defaultValue = "fr") String lang) {
        String langLabel = "fr".equalsIgnoreCase(lang) ? "français" : "anglais";
        AsyncJob job = jobService.create(principal.getUser(),
                JobType.GUIDE_TRANSLATION, "Traduction du guide en " + langLabel);
        asyncJobRunner.runGuideTranslation(job.getId(), principal.getId(), guideId, lang);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobService.toResponse(job));
    }

    @GetMapping("/{guideId}/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID guideId) {

        // Verify access
        guideService.getById(guideId, principal.getId());

        StudyGuide guide = guideService.getEntity(guideId);
        byte[] pdf = pdfExportService.exportGuide(guide);

        String filename = guide.getTitle().replaceAll("[^a-zA-Z0-9 ]", "")
                .replaceAll("\\s+", "_") + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    @DeleteMapping("/{guideId}")
    public ResponseEntity<Void> delete(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID guideId) {
        guideService.delete(guideId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
