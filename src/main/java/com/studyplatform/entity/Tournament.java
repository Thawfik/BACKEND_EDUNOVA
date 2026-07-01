package com.studyplatform.entity;

import com.studyplatform.enums.TournamentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tournaments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @NotBlank
    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TournamentStatus status = TournamentStatus.DRAFT;

    private Instant startDate;

    private Instant endDate;

    @Builder.Default
    private int maxParticipants = 100;

    @Builder.Default
    private int maxTeamSize = 1;

    @Builder.Default
    private boolean allowSolo = true;

    @Column(columnDefinition = "TEXT")
    private String constraints;

    @Column(unique = true)
    private String inviteToken;

    @Builder.Default
    private boolean aiGenerated = false;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}