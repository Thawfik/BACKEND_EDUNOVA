package com.studyplatform.repository;

import com.studyplatform.entity.TournamentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TournamentSubmissionRepository extends JpaRepository<TournamentSubmission, UUID> {

    List<TournamentSubmission> findByParticipantId(UUID participantId);

    Optional<TournamentSubmission> findByParticipantIdAndQuestionId(UUID participantId, UUID questionId);

    List<TournamentSubmission> findByParticipantTournamentId(UUID tournamentId);
}