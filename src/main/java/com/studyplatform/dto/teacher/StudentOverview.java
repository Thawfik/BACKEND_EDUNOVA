package com.studyplatform.dto.teacher;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class StudentOverview {
    private UUID userId;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private int totalXp;
    private int level;
    private int guidesCompleted;
    private int quizzesTaken;
    private Double averageQuizScore;
    /** ACTIVE | STRUGGLING | INACTIVE */
    private String status;
    /** Names of the teacher's groups this student belongs to (used in the global view). */
    private List<String> groupNames;
}