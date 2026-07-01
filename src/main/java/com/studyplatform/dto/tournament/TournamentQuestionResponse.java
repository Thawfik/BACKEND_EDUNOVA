package com.studyplatform.dto.tournament;

import com.studyplatform.enums.Difficulty;
import com.studyplatform.enums.TournamentQuestionType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TournamentQuestionResponse {

    private UUID id;
    private TournamentQuestionType type;
    private String contentJson;
    private int points;
    private Difficulty difficulty;
    private int orderIndex;
    private int timeLimit;
}