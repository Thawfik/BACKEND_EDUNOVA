package com.studyplatform.entity;

import com.studyplatform.enums.SubmissionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tournament_submissions",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"participant_id", "question_id"})
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private TournamentParticipant participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private TournamentQuestion question;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Builder.Default
    private int pointsEarned = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.PENDING;

    private Boolean isCorrect;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant submittedAt;
}