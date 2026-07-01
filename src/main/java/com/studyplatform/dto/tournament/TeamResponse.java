package com.studyplatform.dto.tournament;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TeamResponse {

    private UUID id;
    private String name;
    private UUID leaderId;
    private String leaderName;
    private String inviteCode;
    private int score;
    private int rank;
    private int memberCount;
    private List<ParticipantInfo> members;
}