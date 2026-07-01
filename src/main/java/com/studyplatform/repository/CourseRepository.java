package com.studyplatform.repository;

import com.studyplatform.entity.Course;
import com.studyplatform.entity.User;
import com.studyplatform.enums.CourseStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findByAuthorOrderByUpdatedAtDesc(User author);

    List<Course> findByStatusOrderByPublishedAtDesc(CourseStatus status, Pageable pageable);

    List<Course> findByStatusAndDomainOrderByPublishedAtDesc(CourseStatus status, String domain, Pageable pageable);

    Optional<Course> findBySlugAndStatus(String slug, CourseStatus status);

    boolean existsBySlug(String slug);
}
