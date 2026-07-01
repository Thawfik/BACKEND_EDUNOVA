package com.studyplatform.entity;

import com.studyplatform.enums.JobStatus;
import com.studyplatform.enums.JobType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A long-running background task (AI generation, translation, …) tracked so the
 * UI can fire-and-forget: start the job, keep working, and be notified over
 * WebSocket when it completes. Persisted so the task tray survives a reload.
 */
@Entity
@Table(name = "async_jobs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AsyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    /** Human-readable label shown in the task tray, e.g. "Génération du guide : Réseaux". */
    @Column(nullable = false)
    private String title;

    /** Id of the entity produced by the job (guide, quiz…) — used for navigation. */
    private UUID resultId;

    /** Kind of result for the frontend router, e.g. "guide", "quiz", "guide-translation". */
    private String resultType;

    /** Inline result for jobs that don't persist an entity (translation, …). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String resultPayload;

    @Column(length = 2000)
    private String errorMessage;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    private Instant completedAt;
}