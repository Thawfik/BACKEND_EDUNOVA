package com.studyplatform.dto.stats;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DashboardStatsResponse {
    private int totalXp;
    private long totalStudyMinutes;
    private double totalHours;
    private int guidesCompleted;
    private int quizzesTaken;
    private Double averageQuizScore;
    private int currentStreak;
    private double weeklyHours;
    private int weeklyGuides;
    private int weeklyQuizzes;
    private List<TopicXp> xpByTopic;
    private List<RecentActivity> recentActivity;
    private List<RecentGuide> recentGuides;

    @Data
    @Builder
    public static class TopicXp {
        private String topic;
        private long xp;
    }

    @Data
    @Builder
    public static class RecentActivity {
        private String type;
        private String description;
        private int xpEarned;
        private String timestamp;
    }

    @Data
    @Builder
    public static class RecentGuide {
        private java.util.UUID id;
        private String title;
        private String topic;
        private int moduleCount;
        private java.time.Instant createdAt;
    }
}
