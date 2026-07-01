package com.studyplatform.dto.tournament;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateTeamRequest {

    @NotBlank
    private String name;

    private List<String> memberEmails;
}