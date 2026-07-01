package com.studyplatform.dto.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.studyplatform.enums.CourseStatus;
import com.studyplatform.enums.EducationLevel;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CourseResponse {
    private UUID id;
    private String title;
    private String slug;
    private String summary;
    private String domain;
    private EducationLevel level;
    private String coverImageUrl;
    private JsonNode content;
    private CourseStatus status;
    private int viewCount;
    private UUID authorId;
    private String authorName;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
