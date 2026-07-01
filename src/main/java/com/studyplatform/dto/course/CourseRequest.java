package com.studyplatform.dto.course;

import com.fasterxml.jackson.databind.JsonNode;
import com.studyplatform.enums.EducationLevel;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CourseRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String summary;

    private String domain;

    private EducationLevel level;

    private String coverImageUrl;

    // {"chapters":[{"id","title","blocks":[{"type",...}]}]}
    private JsonNode content;
}
