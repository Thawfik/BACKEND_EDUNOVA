package com.studyplatform.scheduler;

import com.studyplatform.entity.Tournament;
import com.studyplatform.enums.TournamentStatus;
import com.studyplatform.repository.TournamentRepository;
import com.studyplatform.service.TournamentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TournamentScheduler {

    private final TournamentRepository tournamentRepository;
    private final TournamentService tournamentService;
    private final SimpMessagingTemplate messagingTemplate;

    /** Every 30 seconds: auto-start REGISTRATION tournaments whose startDate has passed. */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void autoStart() {
        Instant now = Instant.now();
        List<Tournament> toStart = tournamentRepository.findByStatusIn(
                List.of(TournamentStatus.REGISTRATION))
                .stream()
                .filter(t -> t.getStartDate() != null && t.getStartDate().isBefore(now))
                .toList();

        for (Tournament t : toStart) {
            t.setStatus(TournamentStatus.ACTIVE);
            tournamentRepository.save(t);
            broadcastStatus(t);
            log.info("Auto-started tournament '{}' ({})", t.getTitle(), t.getId());
        }
    }

    /** Every 30 seconds: auto-end ACTIVE tournaments whose endDate has passed. */
    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    @Transactional
    public void autoEnd() {
        Instant now = Instant.now();
        List<Tournament> toEnd = tournamentRepository.findByStatusIn(
                List.of(TournamentStatus.ACTIVE))
                .stream()
                .filter(t -> t.getEndDate() != null && t.getEndDate().isBefore(now))
                .toList();

        for (Tournament t : toEnd) {
            t.setStatus(TournamentStatus.ENDED);
            tournamentRepository.save(t);
            tournamentService.triggerEndRewards(t.getId());
            broadcastStatus(t);
            log.info("Auto-ended tournament '{}' ({})", t.getTitle(), t.getId());
        }
    }

    private void broadcastStatus(Tournament t) {
        messagingTemplate.convertAndSend(
                "/topic/tournament/" + t.getId() + "/status",
                Map.of("tournamentId", t.getId(),
                       "status", t.getStatus().name(),
                       "timestamp", Instant.now().toString()));
    }
}