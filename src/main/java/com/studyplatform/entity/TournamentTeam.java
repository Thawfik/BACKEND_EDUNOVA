package com.studyplatform.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tournament_teams")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_id", nullable = false)
    private User leader;

    @Column(unique = true)
    private String inviteCode;

    @Builder.Default
    private int score = 0;

    @Builder.Default
    private int rank = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}