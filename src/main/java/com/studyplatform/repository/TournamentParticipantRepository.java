package com.studyplatform.repository;

import com.studyplatform.entity.TournamentParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TournamentParticipantRepository extends JpaRepository<TournamentParticipant, UUID> {

    List<TournamentParticipant> findByTournamentIdOrderByScoreDesc(UUID tournamentId);

    Optional<TournamentParticipant> findByTournamentIdAndUserId(UUID tournamentId, UUID userId);

    List<TournamentParticipant> findByTeamId(UUID teamId);

    long countByTournamentId(UUID tournamentId);
}