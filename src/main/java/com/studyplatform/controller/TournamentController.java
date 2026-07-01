package com.studyplatform.controller;

import com.studyplatform.dto.tournament.*;
import com.studyplatform.entity.TournamentParticipant;
import com.studyplatform.entity.TournamentSubmission;
import com.studyplatform.security.CurrentUser;
import com.studyplatform.security.UserPrincipal;
import com.studyplatform.service.TournamentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final TournamentService tournamentService;

    // ── Public / Student endpoints ────────────────────────────

    @GetMapping
    public ResponseEntity<List<TournamentResponse>> listPublic(
            @CurrentUser UserPrincipal principal) {
        return ResponseEntity.ok(tournamentService.listPublic(principal.getUser()));
    }

    @GetMapping("/{tournamentId}")
    public ResponseEntity<TournamentResponse> getById(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID tournamentId) {
        return ResponseEntity.ok(tournamentService.getById(tournamentId, principal.getUser()));
    }

    @GetMapping("/invite/{token}")
    public ResponseEntity<TournamentResponse> getByInviteToken(
            @CurrentUser UserPrincipal principal,
            @PathVariable String token) {
        return ResponseEntity.ok(tournamentService.getByInviteToken(token, principal.getUser()));
    }

    @PostMapping("/{tournamentId}/join")
    public ResponseEntity<ParticipantInfo> joinSolo(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID tournamentId) {
        TournamentParticipant participant = tournamentService.joinSolo(tournamentId, principal.getUser());
        ParticipantInfo info = ParticipantInfo.builder()
                .userId(participant.getUser().getId())
                .fullName(participant.getUser().getFullName())
                .score(participant.getScore())
                .status(participant.getStatus())
                .teamId(participant.getTeam() != null ? participant.getTeam().getId() : null)
                .teamName(participant.getTeam() != null ? participant.getTeam().getName() : null)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(info);
    }

    @PostMapping("/{tournamentId}/teams")
    public ResponseEntity<TeamResponse> createTeam(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID tournamentId,
            @Valid @RequestBody CreateTeamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tournamentService.createTeam(tournamentId, principal.getUser(), request));
    }

    @PostMapping("/teams/join/{inviteCode}")
    public ResponseEntity<TeamResponse> joinTeam(
            @CurrentUser UserPrincipal principal,
            @PathVariable String inviteCode) {
        return ResponseEntity.ok(tournamentService.joinTeam(inviteCode, principal.getUser()));
    }

    @GetMapping("/{tournamentId}/questions")
    public ResponseEntity<List<TournamentQuestionResponse>> getQuestions(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID tournamentId) {
        return ResponseEntity.ok(tournamentService.getQuestions(tournamentId, principal.getUser()));
    }

    @PostMapping("/{tournamentId}/submit")
    public ResponseEntity<SubmissionResult> submitAnswer(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID tournamentId,
            @Valid @RequestBody SubmitAnswerRequest request) {
        TournamentSubmission submission = tournamentService.submitAnswer(tournamentId, principal.getUser(), request);
        SubmissionResult result = SubmissionResult.builder()
                .submissionId(submission.getId())
                .questionId(submission.getQuestion().getId())
                .status(submission.getStatus().name())
                .isCorrect(submission.getIsCorrect())
                .pointsEarned(submission.getPointsEarned())
                .build();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{tournamentId}/run")
    public ResponseEntity<CodeRunResult> runCode(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID tournamentId,
            @Valid @RequestBody RunCodeRequest request) {
        return ResponseEntity.ok(tournamentService.runCode(tournamentId, principal.getUser(), request));
    }

    @GetMapping("/{tournamentId}/leaderboard")
    public ResponseEntity<LeaderboardResponse> getLeaderboard(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID tournamentId) {
        return ResponseEntity.ok(tournamentService.getLeaderboard(tournamentId));
    }

    // ── Teacher-only endpoints ────────────────────────────────

    @PostMapping
    public ResponseEntity<TournamentResponse> create(
            @CurrentUser UserPrincipal principal,
            @Valid @RequestBody CreateTournamentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tournamentService.create(principal.getUser(), request));
    }

    @PostMapping("/{tournamentId}/publish")
    public ResponseEntity<TournamentResponse> publish(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID tournamentId) {
        return ResponseEntity.ok(tournamentService.publish(tournamentId, principal.getUser()));
    }

    @PostMapping("/{tournamentId}/start")
    public ResponseEntity<TournamentResponse> startTournament(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID tournamentId) {
        return ResponseEntity.ok(tournamentService.startTournament(tournamentId, principal.getUser()));
    }

    @PostMapping("/{tournamentId}/end")
    public ResponseEntity<TournamentResponse> endTournament(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID tournamentId) {
        return ResponseEntity.ok(tournamentService.endTournament(tournamentId, principal.getUser()));
    }

    @PatchMapping("/{tournamentId}/dates")
    public ResponseEntity<TournamentResponse> updateDates(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID tournamentId,
            @RequestBody UpdateTournamentDatesRequest request) {
        return ResponseEntity.ok(tournamentService.updateDates(tournamentId, principal.getUser(), request));
    }

    @DeleteMapping("/{tournamentId}")
    public ResponseEntity<Void> withdraw(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID tournamentId) {
        tournamentService.withdraw(tournamentId, principal.getUser());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{tournamentId}/questions")
    public ResponseEntity<TournamentQuestionResponse> addQuestion(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID tournamentId,
            @RequestBody AddQuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tournamentService.addQuestion(tournamentId, principal.getUser(), request));
    }

    @DeleteMapping("/{tournamentId}/questions/{questionId}")
    public ResponseEntity<Void> deleteQuestion(
            @CurrentUser UserPrincipal principal,
            @PathVariable UUID tournamentId,
            @PathVariable UUID questionId) {
        tournamentService.deleteQuestion(questionId, principal.getUser());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mine")
    public ResponseEntity<List<TournamentResponse>> listMine(
            @CurrentUser UserPrincipal principal) {
        return ResponseEntity.ok(tournamentService.listMine(principal.getUser()));
    }

    /** Returns whether the current user is a member of any group created by a teacher. */
    @GetMapping("/group-access")
    public ResponseEntity<java.util.Map<String, Boolean>> hasGroupAccess(
            @CurrentUser UserPrincipal principal) {
        boolean access = tournamentService.hasGroupAccess(principal.getUser());
        return ResponseEntity.ok(java.util.Map.of("hasAccess", access));
    }

    // ── Inner DTO for submission response ────────────────────

    @lombok.Data
    @lombok.Builder
    public static class SubmissionResult {
        private UUID submissionId;
        private UUID questionId;
        private String status;
        private Boolean isCorrect;
        private int pointsEarned;
    }
}