package com.studyplatform.entity;

import com.studyplatform.enums.ParticipantStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tournament_participants",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"tournament_id", "user_id"})
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private TournamentTeam team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ParticipantStatus status = ParticipantStatus.REGISTERED;

    @Builder.Default
    private int score = 0;

    @Builder.Default
    private int rank = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant registeredAt;
}