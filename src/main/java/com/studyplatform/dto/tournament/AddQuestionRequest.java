package com.studyplatform.dto.tournament;

import com.studyplatform.enums.Difficulty;
import com.studyplatform.enums.TournamentQuestionType;
import lombok.Data;

@Data
public class AddQuestionRequest {

    private TournamentQuestionType type;

    private String contentJson;

    private int points;

    private Difficulty difficulty;

    private int orderIndex;

    private int timeLimit = 0;
}