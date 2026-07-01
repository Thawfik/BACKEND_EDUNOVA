package com.studyplatform.dto.teacher;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Aggregate view across every group the teacher belongs to: global KPIs, the
 * per-group breakdown, and the de-duplicated list of all their students.
 */
@Data
@Builder
public class TeacherDashboard {
    private int totalGroups;
    private int totalStudents;
    private int totalXp;
    private Double averageQuizScore;
    private int strugglingCount;
    private int inactiveCount;
    private int activeCount;
    /** Sum of quizzes taken across all students. */
    private int totalQuizzesTaken;
    /** Sum of guides completed across all students. */
    private int totalGuidesCompleted;

    private List<GroupOverview> groups;
    /** Every student across all groups, de-duplicated, with their group names. */
    private List<StudentOverview> students;
}