package com.studyplatform.repository;

import com.studyplatform.entity.StudyGuide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StudyGuideRepository extends JpaRepository<StudyGuide, UUID> {

    List<StudyGuide> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<StudyGuide> findByTopicIdOrderByCreatedAtDesc(UUID topicId);

    long countByUserIdAndCreatedAtAfter(UUID userId, java.time.Instant after);

    /** Unlink guides from a document before the document is deleted (content is self-contained). */
    @Modifying
    @Query("UPDATE StudyGuide g SET g.document = null WHERE g.document.id = :documentId")
    void clearDocumentReference(@Param("documentId") UUID documentId);
}
