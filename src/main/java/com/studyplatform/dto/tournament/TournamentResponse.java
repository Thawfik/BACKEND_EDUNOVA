package com.studyplatform.dto.tournament;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.studyplatform.enums.TournamentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class TournamentResponse {

    private UUID id;
    private String title;
    private String description;
    private UUID teacherId;
    private String teacherName;
    private TournamentStatus status;
    private Instant startDate;
    private Instant endDate;
    private int maxParticipants;
    private int maxTeamSize;
    private boolean allowSolo;
    private String inviteToken;
    private int participantCount;
    private boolean aiGenerated;
    private Instant createdAt;
    private int questionCount;
    @JsonProperty("isRegistered")
    private boolean isRegistered;
}