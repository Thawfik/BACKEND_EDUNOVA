package com.studyplatform.entity;

import com.studyplatform.enums.CourseStatus;
import com.studyplatform.enums.EducationLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A free course written and published by a teacher (OpenClassrooms-style).
 * The body is a JSON document: {"chapters":[{"id","title","blocks":[{"type",...}]}]}.
 */
@Entity
@Table(name = "courses", indexes = {
        @Index(name = "idx_course_status", columnList = "status"),
        @Index(name = "idx_course_slug", columnList = "slug")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false)
    private String title;

    @Column(unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String summary;

    // Free-text domain (matches the onboarding domain labels, e.g. "Informatique")
    private String domain;

    @Enumerated(EnumType.STRING)
    private EducationLevel level;

    private String coverImageUrl;

    // Chapters + blocks as a JSON document
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CourseStatus status = CourseStatus.DRAFT;

    @Builder.Default
    @Column(nullable = false)
    private int viewCount = 0;

    private Instant publishedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
