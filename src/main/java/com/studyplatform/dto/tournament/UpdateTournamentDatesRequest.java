package com.studyplatform.dto.tournament;

import lombok.Data;

import java.time.Instant;

@Data
public class UpdateTournamentDatesRequest {
    private Instant startDate;
    private Instant endDate;
}