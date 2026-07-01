package com.studyplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.ai.AiService;
import com.studyplatform.dto.tournament.*;
import com.studyplatform.entity.*;
import com.studyplatform.enums.*;
import com.studyplatform.exception.ApiException;
import com.studyplatform.entity.GroupMember;
import com.studyplatform.enums.AccountType;
import com.studyplatform.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final TournamentQuestionRepository questionRepository;
    private final TournamentTeamRepository teamRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentSubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final BadgeService badgeService;
    private final XpService xpService;
    private final AiService aiService;
    private final CodeImageRenderer codeImageRenderer;
    private final CodeExecutionService codeExecutionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    // ── Teacher: Create / Manage ──────────────────────────────

    @Transactional
    public TournamentResponse create(User teacher, CreateTournamentRequest req) {
        if (teacher.getAccountType() != AccountType.TEACHER) {
            throw ApiException.forbidden("Only teachers can create tournaments");
        }

        String inviteToken = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        String constraintsJson = buildConstraintsJson(req);

        Tournament tournament = Tournament.builder()
                .teacher(teacher)
                .title(req.getTitle())
                .description(req.getDescription())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .maxParticipants(req.getMaxParticipants() > 0 ? req.getMaxParticipants() : 100)
                .maxTeamSize(req.getMaxTeamSize() > 0 ? req.getMaxTeamSize() : 1)
                .allowSolo(req.isAllowSolo())
                .constraints(constraintsJson)
                .inviteToken(inviteToken)
                .aiGenerated(req.isAiGenerateQuestions())
                .status(TournamentStatus.DRAFT)
                .build();

        tournament = tournamentRepository.save(tournament);

        if (req.isAiGenerateQuestions()) {
            generateAiQuestions(tournament, req);
        }

        return toResponse(tournament, null);
    }

    @Transactional
    public TournamentResponse publish(UUID tournamentId, User teacher) {
        Tournament tournament = getTournamentOrThrow(tournamentId);
        validateTeacherOwnership(tournament, teacher);
        if (tournament.getStatus() != TournamentStatus.DRAFT) {
            throw ApiException.badRequest("Tournament can only be published from DRAFT status");
        }
        tournament.setStatus(TournamentStatus.REGISTRATION);
        tournament = tournamentRepository.save(tournament);
        broadcastStatusChange(tournament);
        return toResponse(tournament, null);
    }

    @Transactional
    public TournamentResponse startTournament(UUID tournamentId, User teacher) {
        Tournament tournament = getTournamentOrThrow(tournamentId);
        validateTeacherOwnership(tournament, teacher);
        if (tournament.getStatus() != TournamentStatus.REGISTRATION) {
            throw ApiException.badRequest("Tournament must be in REGISTRATION status to start");
        }
        tournament.setStatus(TournamentStatus.ACTIVE);
        tournament = tournamentRepository.save(tournament);
        broadcastStatusChange(tournament);
        return toResponse(tournament, null);
    }

    @Transactional
    public TournamentResponse endTournament(UUID tournamentId, User teacher) {
        Tournament tournament = getTournamentOrThrow(tournamentId);
        validateTeacherOwnership(tournament, teacher);
        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw ApiException.badRequest("Tournament must be ACTIVE to end");
        }
        tournament.setStatus(TournamentStatus.ENDED);
        tournament = tournamentRepository.save(tournament);

        // Award XP and badges to top participants
        awardEndOfTournamentRewards(tournament);

        // Broadcast final leaderboard
        broadcastLeaderboard(tournamentId);
        broadcastStatusChange(tournament);

        return toResponse(tournament, null);
    }

    @Transactional
    public TournamentQuestionResponse addQuestion(UUID tournamentId, User teacher, AddQuestionRequest req) {
        Tournament tournament = getTournamentOrThrow(tournamentId);
        validateTeacherOwnership(tournament, teacher);

        TournamentQuestion question = TournamentQuestion.builder()
                .tournament(tournament)
                .type(req.getType())
                .content(req.getContentJson())
                .points(req.getPoints())
                .difficulty(req.getDifficulty())
                .orderIndex(req.getOrderIndex())
                .timeLimit(req.getTimeLimit())
                .build();

        question = questionRepository.save(question);
        return toQuestionResponse(question);
    }

    @Transactional
    public void deleteQuestion(UUID questionId, User teacher) {
        TournamentQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> ApiException.notFound("Question not found"));
        validateTeacherOwnership(question.getTournament(), teacher);
        questionRepository.delete(question);
    }

    @Transactional
    public TournamentResponse updateDates(UUID tournamentId, User teacher, UpdateTournamentDatesRequest req) {
        Tournament tournament = getTournamentOrThrow(tournamentId);
        validateTeacherOwnership(tournament, teacher);

        if (tournament.getStatus() == TournamentStatus.ENDED) {
            throw ApiException.badRequest("Impossible de modifier les dates d'un tournoi terminé");
        }

        if (tournament.getStatus() == TournamentStatus.ACTIVE) {
            // Tournoi en cours : on ne peut que modifier la date de fin
            if (req.getEndDate() == null) {
                throw ApiException.badRequest("La date de fin est requise pour un tournoi en cours");
            }
            if (!req.getEndDate().isAfter(Instant.now())) {
                throw ApiException.badRequest("La nouvelle date de fin doit être dans le futur");
            }
            tournament.setEndDate(req.getEndDate());
        } else {
            // DRAFT ou REGISTRATION : on peut modifier les deux dates
            if (req.getStartDate() != null) tournament.setStartDate(req.getStartDate());
            if (req.getEndDate() != null)   tournament.setEndDate(req.getEndDate());

            if (tournament.getStartDate() != null && tournament.getEndDate() != null
                    && !tournament.getStartDate().isBefore(tournament.getEndDate())) {
                throw ApiException.badRequest("La date de début doit être antérieure à la date de fin");
            }
        }

        tournament = tournamentRepository.save(tournament);
        broadcastStatusChange(tournament);
        return toResponse(tournament, null);
    }

    @Transactional
    public void withdraw(UUID tournamentId, User teacher) {
        Tournament tournament = getTournamentOrThrow(tournamentId);
        validateTeacherOwnership(tournament, teacher);

        if (tournament.getStatus() == TournamentStatus.ACTIVE) {
            throw ApiException.badRequest("Impossible de supprimer un tournoi en cours. Terminez-le d'abord.");
        }

        // Suppression en cascade manuelle : soumissions → participants → équipes → questions → tournoi
        List<TournamentParticipant> participants =
                participantRepository.findByTournamentIdOrderByScoreDesc(tournamentId);
        for (TournamentParticipant p : participants) {
            submissionRepository.deleteAll(submissionRepository.findByParticipantId(p.getId()));
        }
        participantRepository.deleteAll(participants);
        teamRepository.deleteAll(teamRepository.findByTournamentIdOrderByScoreDesc(tournamentId));
        questionRepository.deleteAll(questionRepository.findByTournamentIdOrderByOrderIndex(tournamentId));
        tournamentRepository.delete(tournament);

        log.info("Tournoi '{}' ({}) retiré par {}", tournament.getTitle(), tournamentId, teacher.getEmail());
    }

    // ── Public Queries ────────────────────────────────────────

    public List<TournamentResponse> listPublic(User user) {
        // Teachers don't browse this list; students only see tournaments from their groups' teachers
        List<Tournament> tournaments = tournamentRepository.findVisibleToUser(
                user.getId(),
                List.of(TournamentStatus.REGISTRATION, TournamentStatus.ACTIVE, TournamentStatus.ENDED));
        return tournaments.stream()
                .map(t -> toResponse(t, user))
                .collect(Collectors.toList());
    }

    public List<TournamentResponse> listMine(User teacher) {
        if (teacher.getAccountType() != AccountType.TEACHER) {
            throw ApiException.forbidden("Only teachers can access this endpoint");
        }
        List<Tournament> tournaments = tournamentRepository.findByTeacherIdOrderByCreatedAtDesc(teacher.getId());
        return tournaments.stream()
                .map(t -> toResponse(t, teacher))
                .collect(Collectors.toList());
    }

    public TournamentResponse getById(UUID tournamentId, User user) {
        Tournament tournament = getTournamentOrThrow(tournamentId);
        // Teachers always see their own tournaments
        if (user.getAccountType() == AccountType.TEACHER &&
                tournament.getTeacher().getId().equals(user.getId())) {
            return toResponse(tournament, user);
        }
        // Students: must be a group member of the teacher OR already registered
        boolean hasAccess = isUserInTeacherGroups(user.getId(), tournament.getTeacher().getId())
                || participantRepository.findByTournamentIdAndUserId(tournamentId, user.getId()).isPresent();
        if (!hasAccess) {
            throw ApiException.forbidden("Accès refusé — utilisez le lien d'invitation pour rejoindre ce tournoi");
        }
        return toResponse(tournament, user);
    }

    /** Invite-link endpoint: bypasses group-membership check — any logged-in user can access. */
    public TournamentResponse getByInviteToken(String token, User user) {
        Tournament tournament = tournamentRepository.findByInviteToken(token)
                .orElseThrow(() -> ApiException.notFound("Lien d'invitation invalide ou tournoi introuvable"));
        return toResponse(tournament, user);
    }

    // ── Participant: Join ─────────────────────────────────────

    @Transactional
    public TournamentParticipant joinSolo(UUID tournamentId, User user) {
        Tournament tournament = getTournamentOrThrow(tournamentId);
        if (tournament.getStatus() != TournamentStatus.REGISTRATION) {
            throw ApiException.badRequest("Les inscriptions sont fermées pour ce tournoi");
        }
        if (!tournament.isAllowSolo()) {
            throw ApiException.badRequest("This tournament does not allow solo participation");
        }

        Optional<TournamentParticipant> existing =
                participantRepository.findByTournamentIdAndUserId(tournamentId, user.getId());
        if (existing.isPresent()) {
            throw ApiException.conflict("You are already registered for this tournament");
        }

        long count = participantRepository.countByTournamentId(tournamentId);
        if (count >= tournament.getMaxParticipants()) {
            throw ApiException.badRequest("Tournament is full");
        }

        TournamentParticipant participant = TournamentParticipant.builder()
                .tournament(tournament)
                .user(user)
                .team(null)
                .status(ParticipantStatus.REGISTERED)
                .build();

        return participantRepository.save(participant);
    }

    @Transactional
    public TeamResponse createTeam(UUID tournamentId, User leader, CreateTeamRequest req) {
        Tournament tournament = getTournamentOrThrow(tournamentId);
        if (tournament.getStatus() != TournamentStatus.REGISTRATION) {
            throw ApiException.badRequest("Les inscriptions sont fermées pour ce tournoi");
        }

        // Check leader not already registered
        participantRepository.findByTournamentIdAndUserId(tournamentId, leader.getId())
                .ifPresent(p -> { throw ApiException.conflict("You are already registered for this tournament"); });

        String inviteCode = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        TournamentTeam team = TournamentTeam.builder()
                .tournament(tournament)
                .name(req.getName())
                .leader(leader)
                .inviteCode(inviteCode)
                .build();

        team = teamRepository.save(team);

        // Register the leader as a participant linked to this team
        TournamentParticipant leaderParticipant = TournamentParticipant.builder()
                .tournament(tournament)
                .user(leader)
                .team(team)
                .status(ParticipantStatus.REGISTERED)
                .build();
        participantRepository.save(leaderParticipant);

        // Invite members by email
        List<TournamentParticipant> memberParticipants = new ArrayList<>();
        if (req.getMemberEmails() != null) {
            int maxAdditional = tournament.getMaxTeamSize() - 1;
            List<String> emailsToProcess = req.getMemberEmails().stream()
                    .limit(maxAdditional)
                    .collect(Collectors.toList());

            for (String email : emailsToProcess) {
                Optional<User> memberOpt = userRepository.findByEmail(email);
                if (memberOpt.isPresent()) {
                    User member = memberOpt.get();
                    if (!member.getId().equals(leader.getId())) {
                        Optional<TournamentParticipant> existingMember =
                                participantRepository.findByTournamentIdAndUserId(tournamentId, member.getId());
                        if (existingMember.isEmpty()) {
                            TournamentTeam finalTeam = team;
                            TournamentParticipant memberParticipant = TournamentParticipant.builder()
                                    .tournament(tournament)
                                    .user(member)
                                    .team(finalTeam)
                                    .status(ParticipantStatus.REGISTERED)
                                    .build();
                            memberParticipants.add(participantRepository.save(memberParticipant));
                        }
                    }
                }
            }
        }

        return toTeamResponse(team, leaderParticipant, memberParticipants);
    }

    @Transactional
    public TeamResponse joinTeam(String teamInviteCode, User user) {
        TournamentTeam team = teamRepository.findByInviteCode(teamInviteCode)
                .orElseThrow(() -> ApiException.notFound("Team not found for this invite code"));

        Tournament tournament = team.getTournament();
        if (tournament.getStatus() != TournamentStatus.REGISTRATION) {
            throw ApiException.badRequest("Les inscriptions sont fermées pour ce tournoi");
        }

        // Check user not already registered
        participantRepository.findByTournamentIdAndUserId(tournament.getId(), user.getId())
                .ifPresent(p -> { throw ApiException.conflict("You are already registered for this tournament"); });

        // Check team is not full
        List<TournamentParticipant> currentMembers = participantRepository.findByTeamId(team.getId());
        if (currentMembers.size() >= tournament.getMaxTeamSize()) {
            throw ApiException.badRequest("This team is already full");
        }

        TournamentParticipant participant = TournamentParticipant.builder()
                .tournament(tournament)
                .user(user)
                .team(team)
                .status(ParticipantStatus.REGISTERED)
                .build();
        TournamentParticipant saved = participantRepository.save(participant);

        List<TournamentParticipant> allMembers = participantRepository.findByTeamId(team.getId());
        TournamentParticipant leaderParticipant = allMembers.stream()
                .filter(p -> p.getUser().getId().equals(team.getLeader().getId()))
                .findFirst()
                .orElse(saved);

        List<TournamentParticipant> otherMembers = allMembers.stream()
                .filter(p -> !p.getUser().getId().equals(team.getLeader().getId()))
                .collect(Collectors.toList());

        return toTeamResponse(team, leaderParticipant, otherMembers);
    }

    // ── Competition ───────────────────────────────────────────

    public List<TournamentQuestionResponse> getQuestions(UUID tournamentId, User user) {
        Tournament tournament = getTournamentOrThrow(tournamentId);

        // Le prof propriétaire peut toujours voir ses questions (peu importe le statut)
        boolean isOwner = user.getAccountType() == AccountType.TEACHER
                && tournament.getTeacher().getId().equals(user.getId());

        if (!isOwner) {
            if (tournament.getStatus() != TournamentStatus.ACTIVE) {
                throw ApiException.badRequest("Tournament is not currently active");
            }
            participantRepository.findByTournamentIdAndUserId(tournamentId, user.getId())
                    .orElseThrow(() -> ApiException.forbidden("You are not registered for this tournament"));
        }

        final boolean forStudent = !isOwner;
        return questionRepository.findByTournamentIdOrderByOrderIndex(tournamentId)
                .stream()
                .map(q -> toQuestionResponse(q, forStudent))
                .collect(Collectors.toList());
    }

    @Transactional
    public TournamentSubmission submitAnswer(UUID tournamentId, User user, SubmitAnswerRequest req) {
        Tournament tournament = getTournamentOrThrow(tournamentId);
        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw ApiException.badRequest("Tournament is not currently active");
        }

        TournamentParticipant participant = participantRepository
                .findByTournamentIdAndUserId(tournamentId, user.getId())
                .orElseThrow(() -> ApiException.forbidden("You are not registered for this tournament"));

        TournamentQuestion question = questionRepository.findById(req.getQuestionId())
                .orElseThrow(() -> ApiException.notFound("Question not found"));

        if (!question.getTournament().getId().equals(tournamentId)) {
            throw ApiException.badRequest("Question does not belong to this tournament");
        }

        // Check for existing submission (upsert)
        Optional<TournamentSubmission> existingOpt =
                submissionRepository.findByParticipantIdAndQuestionId(participant.getId(), question.getId());

        TournamentSubmission submission;
        int previousPoints = 0;

        if (existingOpt.isPresent()) {
            submission = existingOpt.get();
            previousPoints = submission.getPointsEarned();
            submission.setAnswer(req.getAnswer());
            submission.setStatus(SubmissionStatus.PENDING);
            submission.setIsCorrect(null);
            submission.setPointsEarned(0);
        } else {
            submission = TournamentSubmission.builder()
                    .participant(participant)
                    .question(question)
                    .answer(req.getAnswer())
                    .status(SubmissionStatus.PENDING)
                    .build();
        }

        // Grade auto-gradable types. ESSAY (and anything else) stays PENDING for manual grading.
        // isCorrect reflects full correctness; pointsEarned may be partial (CODE_CHALLENGE).
        Boolean isCorrect = null;
        int pointsEarned = 0;

        switch (question.getType()) {
            case MCQ, TRUE_FALSE -> {
                isCorrect = autoGrade(question, req.getAnswer());
                pointsEarned = calculatePoints(question, isCorrect);
            }
            case CODE_IMAGE -> {
                isCorrect = gradeCodeImage(question, req.getAnswer());
                pointsEarned = calculatePoints(question, isCorrect);
            }
            case CODE_CHALLENGE -> {
                CodeRunResult run = runAgainstTestCases(question, req.getAnswer());
                int total = run.getCases() != null ? run.getCases().size() : 0;
                long passed = total > 0
                        ? run.getCases().stream().filter(CodeRunResult.CaseResult::isPassed).count()
                        : 0;
                isCorrect = run.isAllPassed();
                // Partial credit proportional to the number of test cases passed.
                pointsEarned = total > 0
                        ? (int) Math.round(calculatePoints(question, true) * ((double) passed / total))
                        : 0;
            }
            default -> { /* ESSAY → manual grading */ }
        }

        if (isCorrect != null) {
            submission.setIsCorrect(isCorrect);
            submission.setPointsEarned(pointsEarned);
            submission.setStatus(SubmissionStatus.AUTO_GRADED);

            // Update participant score
            int scoreDelta = pointsEarned - previousPoints;
            participant.setScore(participant.getScore() + scoreDelta);
            participantRepository.save(participant);

            // Update team score if in a team
            if (participant.getTeam() != null) {
                updateTeamScore(participant.getTeam().getId());
            }

            // Broadcast leaderboard update
            broadcastLeaderboard(tournamentId);
        } else {
            submission.setStatus(SubmissionStatus.PENDING);
        }

        return submissionRepository.save(submission);
    }

    public LeaderboardResponse getLeaderboard(UUID tournamentId) {
        return buildLeaderboard(tournamentId);
    }

    // ── Private Helpers ───────────────────────────────────────

    private Tournament getTournamentOrThrow(UUID tournamentId) {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> ApiException.notFound("Tournament not found"));
    }

    /** Returns true if userId and the teacher share at least one common group. */
    private boolean isUserInTeacherGroups(UUID userId, UUID teacherId) {
        return groupMemberRepository.isInSameGroupAs(userId, teacherId);
    }

    /** Returns true if the user shares at least one group with any teacher account. */
    public boolean hasGroupAccess(User user) {
        return groupMemberRepository.isInAnyGroupWithTeacher(user.getId(), AccountType.TEACHER);
    }

    private void validateTeacherOwnership(Tournament tournament, User teacher) {
        if (teacher.getAccountType() != AccountType.TEACHER) {
            throw ApiException.forbidden("Only teachers can perform this action");
        }
        if (!tournament.getTeacher().getId().equals(teacher.getId())) {
            throw ApiException.forbidden("You do not own this tournament");
        }
    }

    private void broadcastLeaderboard(UUID tournamentId) {
        LeaderboardResponse leaderboard = buildLeaderboard(tournamentId);
        messagingTemplate.convertAndSend(
                "/topic/tournament/" + tournamentId + "/leaderboard", leaderboard);
    }

    private void broadcastStatusChange(Tournament tournament) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tournamentId", tournament.getId());
        payload.put("status", tournament.getStatus().name());
        payload.put("timestamp", Instant.now().toString());
        messagingTemplate.convertAndSend(
                "/topic/tournament/" + tournament.getId() + "/status", payload);
    }

    private LeaderboardResponse buildLeaderboard(UUID tournamentId) {
        Tournament tournament = getTournamentOrThrow(tournamentId);
        List<TournamentParticipant> participants =
                participantRepository.findByTournamentIdOrderByScoreDesc(tournamentId);
        List<TournamentTeam> teams =
                teamRepository.findByTournamentIdOrderByScoreDesc(tournamentId);

        List<LeaderboardEntry> entries = new ArrayList<>();

        if (tournament.getMaxTeamSize() > 1 && !teams.isEmpty()) {
            // Team-based leaderboard: each team as one entry
            int rank = 1;
            for (TournamentTeam team : teams) {
                List<TournamentParticipant> members = participantRepository.findByTeamId(team.getId());
                String initials = team.getName().length() >= 2
                        ? team.getName().substring(0, 2).toUpperCase()
                        : team.getName().toUpperCase();

                entries.add(LeaderboardEntry.builder()
                        .rank(rank++)
                        .entityId(team.getId())
                        .name(team.getName())
                        .score(team.getScore())
                        .memberCount(members.size())
                        .isTeam(true)
                        .avatarInitials(initials)
                        .build());
            }

            // Also add solo participants (those without a team) if allowSolo
            if (tournament.isAllowSolo()) {
                List<TournamentParticipant> soloParticipants = participants.stream()
                        .filter(p -> p.getTeam() == null)
                        .collect(Collectors.toList());
                for (TournamentParticipant solo : soloParticipants) {
                    String name = solo.getUser().getFullName();
                    String initials = buildInitials(name);
                    entries.add(LeaderboardEntry.builder()
                            .rank(rank++)
                            .entityId(solo.getUser().getId())
                            .name(name)
                            .score(solo.getScore())
                            .memberCount(1)
                            .isTeam(false)
                            .avatarInitials(initials)
                            .build());
                }
            }
        } else {
            // Solo-only leaderboard
            int rank = 1;
            for (TournamentParticipant p : participants) {
                String name = p.getUser().getFullName();
                String initials = buildInitials(name);
                entries.add(LeaderboardEntry.builder()
                        .rank(rank++)
                        .entityId(p.getUser().getId())
                        .name(name)
                        .score(p.getScore())
                        .memberCount(1)
                        .isTeam(false)
                        .avatarInitials(initials)
                        .build());
            }
        }

        // Sort by score descending and reassign ranks
        entries.sort(Comparator.comparingInt(LeaderboardEntry::getScore).reversed());
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setRank(i + 1);
        }

        return LeaderboardResponse.builder()
                .tournamentId(tournament.getId())
                .tournamentTitle(tournament.getTitle())
                .entries(entries)
                .updatedAt(Instant.now())
                .build();
    }

    private int calculatePoints(TournamentQuestion q, boolean isCorrect) {
        if (!isCorrect) return 0;
        int multiplier = switch (q.getDifficulty()) {
            case EASY -> 1;
            case MEDIUM -> 2;
            case HARD -> 3;
        };
        return q.getPoints() * multiplier;
    }

    private boolean autoGrade(TournamentQuestion question, String answer) {
        try {
            JsonNode content = objectMapper.readTree(question.getContent());

            if (question.getType() == TournamentQuestionType.MCQ) {
                JsonNode options = content.get("options");
                if (options != null && options.isArray()) {
                    for (JsonNode option : options) {
                        boolean isCorrect = option.has("isCorrect") && option.get("isCorrect").asBoolean(false);
                        String text = option.has("text") ? option.get("text").asText("") : "";
                        if (isCorrect && (text.equalsIgnoreCase(answer.trim())
                                || text.toLowerCase().contains(answer.trim().toLowerCase()))) {
                            return true;
                        }
                    }
                }
                return false;
            }

            if (question.getType() == TournamentQuestionType.TRUE_FALSE) {
                JsonNode isTrue = content.get("isTrue");
                if (isTrue != null) {
                    boolean correctAnswer = isTrue.asBoolean(false);
                    String normalizedAnswer = answer.trim().toLowerCase();
                    boolean submittedTrue = normalizedAnswer.equals("true") || normalizedAnswer.equals("yes");
                    boolean submittedFalse = normalizedAnswer.equals("false") || normalizedAnswer.equals("no");
                    if (correctAnswer && submittedTrue) return true;
                    if (!correctAnswer && submittedFalse) return true;
                }
                return false;
            }

        } catch (Exception e) {
            log.warn("Failed to auto-grade question {}: {}", question.getId(), e.getMessage());
        }
        return false;
    }

    /** AI-grade a CODE_IMAGE explanation against the stored sample answer. */
    private boolean gradeCodeImage(TournamentQuestion question, String userAnswer) {
        try {
            JsonNode content = objectMapper.readTree(question.getContent());
            String codeSnippet = content.path("codeSnippet").asText("");
            String sampleAnswer = content.path("sampleAnswer").asText("");
            JsonNode grade = aiService.gradeCodeExplanation(codeSnippet, sampleAnswer, userAnswer);
            return grade.path("correct").asBoolean(false);
        } catch (Exception e) {
            log.warn("Failed to AI-grade CODE_IMAGE question {}: {}", question.getId(), e.getMessage());
            return false;
        }
    }

    /** The "Execute" button: run candidate code against the question's test cases without scoring. */
    public CodeRunResult runCode(UUID tournamentId, User user, RunCodeRequest req) {
        Tournament tournament = getTournamentOrThrow(tournamentId);
        if (tournament.getStatus() != TournamentStatus.ACTIVE) {
            throw ApiException.badRequest("Tournament is not currently active");
        }
        participantRepository.findByTournamentIdAndUserId(tournamentId, user.getId())
                .orElseThrow(() -> ApiException.forbidden("You are not registered for this tournament"));

        TournamentQuestion question = questionRepository.findById(req.getQuestionId())
                .orElseThrow(() -> ApiException.notFound("Question not found"));
        if (!question.getTournament().getId().equals(tournamentId)) {
            throw ApiException.badRequest("Question does not belong to this tournament");
        }
        return runAgainstTestCases(question, req.getCode());
    }

    /** Run code against the question's test cases (or expectedOutput) and report per-case results. */
    private CodeRunResult runAgainstTestCases(TournamentQuestion question, String code) {
        String language = "python";
        List<CodeRunResult.CaseResult> caseResults = new ArrayList<>();
        String lastStdout = "";
        String lastStderr = "";
        boolean anyError = false;

        try {
            JsonNode content = objectMapper.readTree(question.getContent());
            language = content.path("language").asText("python");

            List<JsonNode> cases = new ArrayList<>();
            JsonNode testCases = content.get("testCases");
            if (testCases != null && testCases.isArray() && testCases.size() > 0) {
                testCases.forEach(cases::add);
            }

            if (cases.isEmpty()) {
                // No structured test cases — single run compared to expectedOutput.
                String expected = content.path("expectedOutput").asText("");
                CodeExecutionService.ExecResult exec = codeExecutionService.execute(language, code, "");
                lastStdout = exec.getStdout();
                lastStderr = exec.getStderr();
                boolean passed = !expected.isBlank()
                        ? normalize(exec.getStdout()).equals(normalize(expected))
                        : exec.isSuccess();
                caseResults.add(CodeRunResult.CaseResult.builder()
                        .passed(passed).input("").expected(expected)
                        .actual(exec.getStdout()).build());
                anyError = !exec.isSuccess();
            } else {
                for (JsonNode tc : cases) {
                    String input = tc.path("input").asText("");
                    String expected = tc.path("expected").asText("");
                    CodeExecutionService.ExecResult exec = codeExecutionService.execute(language, code, input);
                    lastStdout = exec.getStdout();
                    if (!exec.getStderr().isBlank()) { lastStderr = exec.getStderr(); anyError = true; }
                    boolean passed = exec.isSuccess()
                            && normalize(exec.getStdout()).equals(normalize(expected));
                    caseResults.add(CodeRunResult.CaseResult.builder()
                            .passed(passed).input(input).expected(expected)
                            .actual(exec.getStdout()).build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to run CODE_CHALLENGE question {}: {}", question.getId(), e.getMessage());
            lastStderr = "Erreur lors de l'exécution.";
            anyError = true;
        }

        boolean allPassed = !caseResults.isEmpty()
                && caseResults.stream().allMatch(CodeRunResult.CaseResult::isPassed);
        return CodeRunResult.builder()
                .success(!anyError)
                .stdout(lastStdout)
                .stderr(lastStderr)
                .cases(caseResults)
                .allPassed(allPassed)
                .build();
    }

    private String normalize(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").trim();
    }

    private void updateTeamScore(UUID teamId) {
        TournamentTeam team = teamRepository.findById(teamId).orElse(null);
        if (team == null) return;

        List<TournamentParticipant> members = participantRepository.findByTeamId(teamId);
        int totalScore = members.stream().mapToInt(TournamentParticipant::getScore).sum();
        team.setScore(totalScore);
        teamRepository.save(team);
    }

    /** Called by the scheduler when a tournament is auto-ended. */
    public void triggerEndRewards(java.util.UUID tournamentId) {
        Tournament tournament = getTournamentOrThrow(tournamentId);
        awardEndOfTournamentRewards(tournament);
        broadcastLeaderboard(tournamentId);
    }

    private void awardEndOfTournamentRewards(Tournament tournament) {
        LeaderboardResponse leaderboard = buildLeaderboard(tournament.getId());
        List<LeaderboardEntry> entries = leaderboard.getEntries();

        for (int i = 0; i < Math.min(3, entries.size()); i++) {
            LeaderboardEntry entry = entries.get(i);
            int xpAmount = switch (i) {
                case 0 -> 100;
                case 1 -> 50;
                default -> 25;
            };

            if (entry.isTeam()) {
                // Award to all team members
                List<TournamentParticipant> members = participantRepository.findByTeamId(entry.getEntityId());
                for (TournamentParticipant member : members) {
                    User memberUser = member.getUser();
                    xpService.awardXp(memberUser, null, xpAmount, "TOURNAMENT",
                            tournament.getId().toString());
                    if (i == 0) {
                        badgeService.awardBadgeByCode(memberUser, "TOURNAMENT_WINNER");
                        badgeService.awardBadgeByCode(memberUser, "TOURNAMENT_PODIUM");
                    } else {
                        badgeService.awardBadgeByCode(memberUser, "TOURNAMENT_PODIUM");
                    }
                }
            } else {
                // Find the solo participant user
                participantRepository.findByTournamentIdAndUserId(
                        tournament.getId(), entry.getEntityId()).ifPresent(p -> {
                    User pUser = p.getUser();
                    xpService.awardXp(pUser, null, xpAmount, "TOURNAMENT",
                            tournament.getId().toString());
                    if (entry.getRank() == 1) {
                        badgeService.awardBadgeByCode(pUser, "TOURNAMENT_WINNER");
                        badgeService.awardBadgeByCode(pUser, "TOURNAMENT_PODIUM");
                    } else {
                        badgeService.awardBadgeByCode(pUser, "TOURNAMENT_PODIUM");
                    }
                });
            }
        }
    }

    /** Default set of question types when the teacher didn't pick any. */
    private static final List<TournamentQuestionType> DEFAULT_TYPES = List.of(
            TournamentQuestionType.MCQ,
            TournamentQuestionType.TRUE_FALSE,
            TournamentQuestionType.CODE_IMAGE,
            TournamentQuestionType.CODE_CHALLENGE);

    private List<TournamentQuestionType> resolveRequestedTypes(CreateTournamentRequest req) {
        if (req.getQuestionTypes() == null || req.getQuestionTypes().isEmpty()) {
            return DEFAULT_TYPES;
        }
        List<TournamentQuestionType> types = new ArrayList<>();
        for (String t : req.getQuestionTypes()) {
            try {
                types.add(TournamentQuestionType.valueOf(t.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // Skip unknown type names rather than failing the whole generation.
            }
        }
        return types.isEmpty() ? DEFAULT_TYPES : types;
    }

    private void generateAiQuestions(Tournament tournament, CreateTournamentRequest req) {
        try {
            List<TournamentQuestionType> types = resolveRequestedTypes(req);

            JsonNode result = aiService.generateTournamentQuestions(
                    req.getTitle(), req.getQuestionCount(), req.getDifficulty(), types);

            JsonNode questions = result.get("questions");
            if (questions == null || !questions.isArray()) return;

            int orderIndex = 0;
            for (JsonNode qNode : questions) {
                try {
                    String typeStr = qNode.has("type") ? qNode.get("type").asText("MCQ") : "MCQ";
                    TournamentQuestionType type;
                    try {
                        type = TournamentQuestionType.valueOf(typeStr);
                    } catch (IllegalArgumentException e) {
                        type = TournamentQuestionType.MCQ;
                    }

                    JsonNode contentNode = qNode.get("content");

                    // For CODE_IMAGE, render the snippet to a real PNG so the candidate
                    // sees an image (not copyable text) and must read & explain it.
                    if (type == TournamentQuestionType.CODE_IMAGE
                            && contentNode instanceof com.fasterxml.jackson.databind.node.ObjectNode obj
                            && obj.hasNonNull("codeSnippet")) {
                        String dataUrl = codeImageRenderer.renderToDataUrl(obj.get("codeSnippet").asText());
                        if (dataUrl != null) {
                            obj.put("imageDataUrl", dataUrl);
                        }
                    }

                    String contentJson = contentNode != null
                            ? objectMapper.writeValueAsString(contentNode)
                            : "{}";

                    int points = qNode.has("points") ? qNode.get("points").asInt(10) : 10;
                    String diffStr = qNode.has("difficulty") ? qNode.get("difficulty").asText("MEDIUM") : "MEDIUM";
                    Difficulty difficulty;
                    try {
                        difficulty = Difficulty.valueOf(diffStr);
                    } catch (IllegalArgumentException e) {
                        difficulty = Difficulty.MEDIUM;
                    }

                    TournamentQuestion question = TournamentQuestion.builder()
                            .tournament(tournament)
                            .type(type)
                            .content(contentJson)
                            .points(points)
                            .difficulty(difficulty)
                            .orderIndex(orderIndex)
                            .timeLimit(0)
                            .build();

                    questionRepository.save(question);
                    orderIndex++;
                } catch (Exception e) {
                    log.warn("Failed to persist AI-generated question: {}", e.getMessage());
                }
            }
            log.info("Generated {} AI questions for tournament {}", orderIndex, tournament.getId());
        } catch (Exception e) {
            log.error("AI question generation failed for tournament {}: {}", tournament.getId(), e.getMessage());
        }
    }

    private String buildConstraintsJson(CreateTournamentRequest req) {
        try {
            Map<String, Object> constraints = new HashMap<>();
            constraints.put("durationMinutes", req.getConstraintsDurationMinutes());
            if (req.getEliminationRules() != null) {
                constraints.put("eliminationRules", req.getEliminationRules());
            }
            return objectMapper.writeValueAsString(constraints);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildInitials(String fullName) {
        if (fullName == null || fullName.isBlank()) return "??";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].charAt(0) + "" + parts[1].charAt(0)).toUpperCase();
        }
        return fullName.length() >= 2
                ? fullName.substring(0, 2).toUpperCase()
                : fullName.toUpperCase();
    }

    // ── Mapping helpers ───────────────────────────────────────

    private TournamentResponse toResponse(Tournament tournament, User currentUser) {
        long participantCount = participantRepository.countByTournamentId(tournament.getId());
        long questionCount = questionRepository.countByTournamentId(tournament.getId());
        boolean isRegistered = false;

        if (currentUser != null) {
            isRegistered = participantRepository
                    .findByTournamentIdAndUserId(tournament.getId(), currentUser.getId())
                    .isPresent();
        }

        return TournamentResponse.builder()
                .id(tournament.getId())
                .title(tournament.getTitle())
                .description(tournament.getDescription())
                .teacherId(tournament.getTeacher().getId())
                .teacherName(tournament.getTeacher().getFullName())
                .status(tournament.getStatus())
                .startDate(tournament.getStartDate())
                .endDate(tournament.getEndDate())
                .maxParticipants(tournament.getMaxParticipants())
                .maxTeamSize(tournament.getMaxTeamSize())
                .allowSolo(tournament.isAllowSolo())
                .inviteToken(tournament.getInviteToken())
                .participantCount((int) participantCount)
                .aiGenerated(tournament.isAiGenerated())
                .createdAt(tournament.getCreatedAt())
                .questionCount((int) questionCount)
                .isRegistered(isRegistered)
                .build();
    }

    private TournamentQuestionResponse toQuestionResponse(TournamentQuestion question) {
        return toQuestionResponse(question, false);
    }

    private TournamentQuestionResponse toQuestionResponse(TournamentQuestion question, boolean forStudent) {
        String content = forStudent ? sanitizeContent(question) : question.getContent();
        return TournamentQuestionResponse.builder()
                .id(question.getId())
                .type(question.getType())
                .contentJson(content)
                .points(question.getPoints())
                .difficulty(question.getDifficulty())
                .orderIndex(question.getOrderIndex())
                .timeLimit(question.getTimeLimit())
                .build();
    }

    /**
     * Strip answer-revealing fields before sending a question to a competitor:
     * MCQ {@code isCorrect}, TRUE_FALSE {@code isTrue}, explanations, the CODE_IMAGE
     * raw snippet (image only) and CODE_CHALLENGE expected outputs. Fails safe to an
     * empty object rather than leaking on a parse error.
     */
    private String sanitizeContent(TournamentQuestion question) {
        try {
            JsonNode node = objectMapper.readTree(question.getContent());
            if (!(node instanceof com.fasterxml.jackson.databind.node.ObjectNode obj)) {
                return question.getContent();
            }
            obj.remove("isTrue");
            obj.remove("explanation");
            obj.remove("sampleAnswer");

            JsonNode options = obj.get("options");
            if (options != null && options.isArray()) {
                for (JsonNode opt : options) {
                    if (opt instanceof com.fasterxml.jackson.databind.node.ObjectNode o) {
                        o.remove("isCorrect");
                    }
                }
            }

            if (question.getType() == TournamentQuestionType.CODE_IMAGE) {
                obj.remove("codeSnippet"); // candidate sees only the rendered image
            }

            JsonNode testCases = obj.get("testCases");
            if (testCases != null && testCases.isArray()) {
                for (JsonNode tc : testCases) {
                    if (tc instanceof com.fasterxml.jackson.databind.node.ObjectNode t) {
                        t.remove("expected");
                    }
                }
            }
            obj.remove("expectedOutput");

            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to sanitize question {}: {}", question.getId(), e.getMessage());
            return "{}";
        }
    }

    private TeamResponse toTeamResponse(TournamentTeam team,
                                         TournamentParticipant leaderParticipant,
                                         List<TournamentParticipant> otherMembers) {
        List<ParticipantInfo> allMemberInfos = new ArrayList<>();
        allMemberInfos.add(toParticipantInfo(leaderParticipant));
        for (TournamentParticipant m : otherMembers) {
            allMemberInfos.add(toParticipantInfo(m));
        }

        return TeamResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .leaderId(team.getLeader().getId())
                .leaderName(team.getLeader().getFullName())
                .inviteCode(team.getInviteCode())
                .score(team.getScore())
                .rank(team.getRank())
                .memberCount(allMemberInfos.size())
                .members(allMemberInfos)
                .build();
    }

    private ParticipantInfo toParticipantInfo(TournamentParticipant participant) {
        return ParticipantInfo.builder()
                .userId(participant.getUser().getId())
                .fullName(participant.getUser().getFullName())
                .score(participant.getScore())
                .status(participant.getStatus())
                .teamId(participant.getTeam() != null ? participant.getTeam().getId() : null)
                .teamName(participant.getTeam() != null ? participant.getTeam().getName() : null)
                .build();
    }
}