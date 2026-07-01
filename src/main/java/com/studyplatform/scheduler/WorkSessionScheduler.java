package com.studyplatform.scheduler;

import com.studyplatform.entity.WorkSession;
import com.studyplatform.enums.NotificationType;
import com.studyplatform.repository.WorkSessionRepository;
import com.studyplatform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pushes a "starting soon" reminder to every member of a group shortly before one
 * of its scheduled work sessions begins. Runs on a fixed delay (same pattern as
 * {@link TournamentScheduler}); each session is reminded exactly once thanks to
 * the {@code reminderSent} flag on {@link WorkSession}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkSessionScheduler {

    /** How long before the start time the reminder is sent. */
    private static final Duration REMINDER_LEAD = Duration.ofMinutes(5);

    private final WorkSessionRepository sessionRepository;
    private final NotificationService notificationService;

    /** Every 30 seconds: remind groups about sessions starting within the lead window. */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void sendUpcomingReminders() {
        Instant now = Instant.now();
        // Window [now - lead, now + lead]: fires up to 5 min early while ignoring
        // sessions whose start is already well in the past (e.g. after a restart).
        List<WorkSession> due = sessionRepository.findByReminderSentFalseAndScheduledAtBetween(
                now.minus(REMINDER_LEAD), now.plus(REMINDER_LEAD));

        for (WorkSession session : due) {
            UUID groupId = session.getGroup().getId();
            long minutes = Math.round(
                    Math.max(0, Duration.between(now, session.getScheduledAt()).getSeconds()) / 60.0);

            String when = minutes <= 0 ? "maintenant" : "dans " + minutes + " min";
            String name = session.getTitle() != null && !session.getTitle().isBlank()
                    ? "« " + session.getTitle() + " »"
                    : "Une session de travail";

            notificationService.notifyGroup(
                    groupId,
                    null, // notify everyone, including the creator
                    NotificationType.GROUP_SESSION_SCHEDULED,
                    "Session de groupe bientôt",
                    name + " commence " + when + ".",
                    "/groups/" + groupId);

            session.setReminderSent(true);
            sessionRepository.save(session);
            log.info("Sent start reminder for work session {} (group {}, in {} min)",
                    session.getId(), groupId, minutes);
        }
    }
}