package com.studyplatform.repository;

import com.studyplatform.entity.WorkSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface WorkSessionRepository extends JpaRepository<WorkSession, UUID> {

    List<WorkSession> findByGroupIdOrderByScheduledAtDesc(UUID groupId);

    List<WorkSession> findByGroupIdAndScheduledAtAfterOrderByScheduledAtAsc(UUID groupId, Instant after);

    /**
     * Sessions awaiting their "starting soon" reminder whose start falls inside the
     * reminder window [now - lead, now + lead]. The lower bound keeps stale past
     * sessions from being blasted on the first scheduler run after a restart.
     */
    List<WorkSession> findByReminderSentFalseAndScheduledAtBetween(Instant start, Instant end);
}
