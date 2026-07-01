package com.studyplatform.dto.tournament;

import com.studyplatform.enums.ParticipantStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ParticipantInfo {

    private UUID userId;
    private String fullName;
    private int score;
    private ParticipantStatus status;
    private UUID teamId;
    private String teamName;
}