package com.studyplatform.controller;

import com.studyplatform.dto.course.CourseResponse;
import com.studyplatform.dto.course.CourseSummaryResponse;
import com.studyplatform.service.CourseService;
import com.studyplatform.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLConnection;
import java.time.Duration;
import java.util.List;

/**
 * Unauthenticated endpoints so visitors (not yet registered) can browse and
 * read published courses, and load their media. Mapped under /api/public/**
 * which is permitted in SecurityConfig.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicCourseController {

    private final CourseService courseService;
    private final StorageService storageService;

    @GetMapping("/courses")
    public ResponseEntity<List<CourseSummaryResponse>> listPublished(
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "limit", defaultValue = "24") int limit) {
        return ResponseEntity.ok(courseService.listPublished(domain, limit));
    }

    @GetMapping("/courses/{slug}")
    public ResponseEntity<CourseResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(courseService.getPublishedBySlug(slug));
    }

    @GetMapping("/media/{*key}")
    public ResponseEntity<Resource> serveMedia(@PathVariable String key) {
        // Strip the leading slash captured by {*key}
        String storageKey = key.startsWith("/") ? key.substring(1) : key;
        Resource resource = storageService.loadAsResource(storageKey);

        String contentType = URLConnection.guessContentTypeFromName(storageKey);
        MediaType mediaType = contentType != null
                ? MediaType.parseMediaType(contentType)
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(resource);
    }
}
