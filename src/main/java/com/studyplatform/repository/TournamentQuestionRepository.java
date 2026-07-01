package com.studyplatform.repository;

import com.studyplatform.entity.TournamentQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TournamentQuestionRepository extends JpaRepository<TournamentQuestion, UUID> {

    List<TournamentQuestion> findByTournamentIdOrderByOrderIndex(UUID tournamentId);

    long countByTournamentId(UUID tournamentId);
}