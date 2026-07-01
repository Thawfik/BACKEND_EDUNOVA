package com.studyplatform.entity;

import com.studyplatform.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A single in-app notification delivered to one user. Persisted so the bell can
 * rebuild its history on reload, and pushed live over WebSocket
 * ({@code /topic/notifications/{recipientId}}) — same pattern as {@link AsyncJob}.
 */
@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(length = 500)
    private String message;

    /** Contextual group this notification relates to (nullable). */
    private UUID groupId;

    /** Frontend route to open when the notification is clicked, e.g. "/groups/{id}". */
    private String linkPath;

    @Builder.Default
    @Column(nullable = false)
    private boolean read = false;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
