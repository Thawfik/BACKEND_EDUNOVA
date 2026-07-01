package com.studyplatform.controller;

import com.studyplatform.dto.course.CourseRequest;
import com.studyplatform.dto.course.CourseResponse;
import com.studyplatform.dto.course.CourseSummaryResponse;
import com.studyplatform.security.CurrentUser;
import com.studyplatform.security.UserPrincipal;
import com.studyplatform.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    public ResponseEntity<CourseResponse> create(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody CourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseService.create(request, principal.getUser()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CourseResponse> update(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CourseRequest request) {
        return ResponseEntity.ok(courseService.update(id, request, principal.getUser()));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<CourseResponse> publish(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(courseService.publish(id, principal.getUser()));
    }

    @PostMapping("/{id}/unpublish")
    public ResponseEntity<CourseResponse> unpublish(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(courseService.unpublish(id, principal.getUser()));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<CourseSummaryResponse>> listMine(@CurrentUser UserPrincipal principal) {
        return ResponseEntity.ok(courseService.listMine(principal.getUser()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseResponse> getOwned(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(courseService.getOwned(id, principal.getUser()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID id) {
        courseService.delete(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/media")
    public ResponseEntity<Map<String, String>> uploadMedia(
            @CurrentUser UserPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        String url = courseService.uploadMedia(file, principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", url));
    }
}
