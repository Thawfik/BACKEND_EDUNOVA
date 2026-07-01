package com.studyplatform.dto.job;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class JobResponse {
    private UUID id;
    private String type;
    private String status;
    private String title;
    private UUID resultId;
    private String resultType;
    private JsonNode resultPayload;
    private String errorMessage;
    private Instant createdAt;
    private Instant completedAt;
}