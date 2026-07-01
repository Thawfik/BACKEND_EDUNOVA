package com.studyplatform.dto.tournament;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class LeaderboardEntry {

    private int rank;
    private UUID entityId;
    private String name;
    private int score;
    private int memberCount;
    private boolean isTeam;
    private String avatarInitials;
}