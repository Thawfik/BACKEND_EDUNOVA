package com.studyplatform.dto.tournament;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class CreateTournamentRequest {

    @NotBlank
    private String title;

    private String description;

    private Instant startDate;

    private Instant endDate;

    private int maxParticipants = 100;

    private int maxTeamSize = 1;

    private boolean allowSolo = true;

    private int constraintsDurationMinutes;

    private String eliminationRules;

    private boolean aiGenerateQuestions;

    private int questionCount = 10;

    private String difficulty = "MEDIUM";

    /** Question types the teacher wants generated (e.g. ["MCQ","TRUE_FALSE","CODE_IMAGE","CODE_CHALLENGE"]). */
    private List<String> questionTypes;
}