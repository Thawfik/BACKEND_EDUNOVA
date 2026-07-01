package com.studyplatform.dto.notification;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {
    private UUID id;
    private String type;
    private String title;
    private String message;
    private UUID groupId;
    private String linkPath;
    private boolean read;
    private Instant createdAt;
}
