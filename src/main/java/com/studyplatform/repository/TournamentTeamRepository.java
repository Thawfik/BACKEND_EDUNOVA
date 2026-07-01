package com.studyplatform.repository;

import com.studyplatform.entity.TournamentTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TournamentTeamRepository extends JpaRepository<TournamentTeam, UUID> {

    List<TournamentTeam> findByTournamentIdOrderByScoreDesc(UUID tournamentId);

    Optional<TournamentTeam> findByInviteCode(String code);

    Optional<TournamentTeam> findByTournamentIdAndLeaderId(UUID tournamentId, UUID leaderId);
}