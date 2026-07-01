package com.studyplatform.repository;

import com.studyplatform.entity.Tournament;
import com.studyplatform.enums.TournamentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, UUID> {

    List<Tournament> findByStatusOrderByCreatedAtDesc(TournamentStatus status);

    List<Tournament> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);

    Optional<Tournament> findByInviteToken(String token);

    List<Tournament> findByStatusIn(List<TournamentStatus> statuses);

    /**
     * Tournaments visible to a user: those whose teacher shares at least one
     * group with the user — regardless of who created that group.
     */
    @Query("SELECT t FROM Tournament t " +
           "WHERE t.status IN :statuses " +
           "AND EXISTS (" +
           "  SELECT gm1 FROM GroupMember gm1 " +
           "  WHERE gm1.user.id = :userId " +
           "  AND EXISTS (" +
           "    SELECT gm2 FROM GroupMember gm2 " +
           "    WHERE gm2.group.id = gm1.group.id " +
           "    AND gm2.user.id = t.teacher.id" +
           "  )" +
           ") " +
           "ORDER BY t.createdAt DESC")
    List<Tournament> findVisibleToUser(@Param("userId") UUID userId,
                                       @Param("statuses") List<TournamentStatus> statuses);
}