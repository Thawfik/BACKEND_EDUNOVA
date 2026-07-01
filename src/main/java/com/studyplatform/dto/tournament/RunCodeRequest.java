package com.studyplatform.dto.tournament;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** Body of the "Execute" button: run the candidate's code against a question's test cases. */
@Data
public class RunCodeRequest {

    @NotNull
    private UUID questionId;

    /** The candidate's source code. */
    private String code;

    /** Optional standard input for a free run (test cases supply their own stdin). */
    private String stdin;
}
