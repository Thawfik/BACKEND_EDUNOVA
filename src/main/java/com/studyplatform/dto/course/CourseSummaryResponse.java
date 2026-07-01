package com.studyplatform.dto.course;

import com.studyplatform.enums.CourseStatus;
import com.studyplatform.enums.EducationLevel;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/** Lightweight projection used for course cards (landing, catalog, teacher list). */
@Data
@Builder
public class CourseSummaryResponse {
    private UUID id;
    private String title;
    private String slug;
    private String summary;
    private String domain;
    private EducationLevel level;
    private String coverImageUrl;
    private CourseStatus status;
    private int viewCount;
    private int chapterCount;
    private String authorName;
    private Instant publishedAt;
    private Instant updatedAt;
}
