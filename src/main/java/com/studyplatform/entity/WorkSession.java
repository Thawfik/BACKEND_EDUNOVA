package com.studyplatform.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private StudyGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    private String title;

    @Column(nullable = false)
    private Instant scheduledAt;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private boolean active;

    /**
     * Whether the "starting soon" reminder has already been pushed to the group.
     * Set once by {@code WorkSessionScheduler} so the reminder fires exactly once.
     */
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean reminderSent = false;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
