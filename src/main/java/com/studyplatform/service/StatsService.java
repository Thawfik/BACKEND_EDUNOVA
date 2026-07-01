package com.studyplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.dto.stats.DashboardStatsResponse;
import com.studyplatform.dto.stats.StudySessionRequest;
import com.studyplatform.entity.StudySession;
import com.studyplatform.entity.Topic;
import com.studyplatform.entity.User;
import com.studyplatform.entity.XpLog;
import com.studyplatform.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatsService {

    private final XpLogRepository xpLogRepository;
    private final StudyGuideRepository guideRepository;
    private final QuizAttemptRepository attemptRepository;
    private final StudySessionRepository sessionRepository;
    private final TopicRepository topicRepository;
    private final ObjectMapper objectMapper;

    public DashboardStatsResponse getDashboardStats(UUID userId) {
        int totalXp = xpLogRepository.getTotalXpByUserId(userId);
        long totalSeconds = sessionRepository.getTotalStudySecondsForUser(userId);
        long guidesCount = guideRepository.findByUserIdOrderByCreatedAtDesc(userId).size();
        long quizzesCount = attemptRepository.countByUserId(userId);
        Double avgScore = attemptRepository.findAverageScoreByUserId(userId);

        // Streak: consecutive days with XP activity (guide or quiz)
        List<XpLog> allLogs = xpLogRepository.findByUserIdOrderByEarnedAtDesc(userId);
        int currentStreak = calculateStreak(allLogs);

        // Weekly stats (last 7 days)
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long weeklySeconds = sessionRepository.getTotalStudySecondsForUserSince(userId, weekAgo);
        long weeklyGuidesCount = guideRepository.countByUserIdAndCreatedAtAfter(userId, weekAgo);
        long weeklyQuizzesCount = attemptRepository.countByUserIdAndCompletedAtAfter(userId, weekAgo);

        List<DashboardStatsResponse.TopicXp> xpByTopic = xpLogRepository.getXpByTopicForUser(userId)
                .stream()
                .map(row -> DashboardStatsResponse.TopicXp.builder()
                        .topic((String) row[0])
                        .xp(((Number) row[1]).longValue())
                        .build())
                .toList();

        List<DashboardStatsResponse.RecentActivity> recent = allLogs
                .stream()
                .limit(10)
                .map(xp -> DashboardStatsResponse.RecentActivity.builder()
                        .type(xp.getSource())
                        .description(xp.getSource() + (xp.getTopic() != null ? " — " + xp.getTopic().getName() : ""))
                        .xpEarned(xp.getXpEarned())
                        .timestamp(xp.getEarnedAt().toString())
                        .build())
                .toList();

        List<DashboardStatsResponse.RecentGuide> recentGuides = guideRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(3)
                .map(g -> {
                    int moduleCount = 0;
                    try {
                        JsonNode content = objectMapper.readTree(g.getContent());
                        if (content.has("modules")) moduleCount = content.get("modules").size();
                    } catch (Exception ignored) {}
                    return DashboardStatsResponse.RecentGuide.builder()
                            .id(g.getId())
                            .title(g.getTitle())
                            .topic(g.getTopic() != null ? g.getTopic().getName() : null)
                            .moduleCount(moduleCount)
                            .createdAt(g.getCreatedAt())
                            .build();
                })
                .toList();

        return DashboardStatsResponse.builder()
                .totalXp(totalXp)
                .totalStudyMinutes(totalSeconds / 60)
                .totalHours(Math.round(totalSeconds / 3600.0 * 10) / 10.0)
                .guidesCompleted((int) guidesCount)
                .quizzesTaken((int) quizzesCount)
                .averageQuizScore(avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : null)
                .currentStreak(currentStreak)
                .weeklyHours(Math.round(weeklySeconds / 3600.0 * 10) / 10.0)
                .weeklyGuides((int) weeklyGuidesCount)
                .weeklyQuizzes((int) weeklyQuizzesCount)
                .xpByTopic(xpByTopic)
                .recentActivity(recent)
                .recentGuides(recentGuides)
                .build();
    }

    private int calculateStreak(List<XpLog> logs) {
        if (logs.isEmpty()) return 0;
        Set<LocalDate> activityDates = logs.stream()
                .map(xp -> xp.getEarnedAt().atZone(ZoneOffset.UTC).toLocalDate())
                .collect(Collectors.toSet());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        // Start from today if active today, otherwise from yesterday
        LocalDate check = activityDates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (activityDates.contains(check)) {
            streak++;
            check = check.minusDays(1);
        }
        return streak;
    }

    @Transactional
    public void logSession(User user, StudySessionRequest request) {
        Topic topic = null;
        if (request.getTopicId() != null) {
            topic = topicRepository.findById(request.getTopicId()).orElse(null);
        }

        StudySession session = StudySession.builder()
                .user(user)
                .topic(topic)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .focusScore(request.getFocusScore())
                .activity(request.getActivity())
                .build();

        sessionRepository.save(session);
        log.info("Study session logged for user {}", user.getEmail());
    }
}
