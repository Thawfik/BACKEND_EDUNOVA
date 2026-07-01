package com.studyplatform.dto.teacher;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class GroupOverview {
    private UUID groupId;
    private String groupName;
    /** All members (teachers + students). */
    private int memberCount;
    /** Members with a STUDENT account only. */
    private int studentCount;
    private int totalGroupXp;
    private Double averageQuizScore;
    /** Number of students flagged STRUGGLING. */
    private int strugglingCount;
    /** Number of students flagged INACTIVE (no activity yet). */
    private int inactiveCount;
    /** Students only (the viewing teacher and other teachers are excluded). */
    private List<StudentOverview> students;
    private Instant createdAt;
}
