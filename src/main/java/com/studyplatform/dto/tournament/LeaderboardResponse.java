package com.studyplatform.dto.tournament;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class LeaderboardResponse {

    private UUID tournamentId;
    private String tournamentTitle;
    private List<LeaderboardEntry> entries;
    private Instant updatedAt;
}