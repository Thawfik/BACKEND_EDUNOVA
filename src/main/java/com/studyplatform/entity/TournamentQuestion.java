package com.studyplatform.entity;

import com.studyplatform.enums.Difficulty;
import com.studyplatform.enums.TournamentQuestionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tournament_questions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TournamentQuestionType type;

    @Column(columnDefinition = "TEXT")
    private String content;

    private int points;

    @Enumerated(EnumType.STRING)
    private Difficulty difficulty;

    private int orderIndex;

    @Builder.Default
    private int timeLimit = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}