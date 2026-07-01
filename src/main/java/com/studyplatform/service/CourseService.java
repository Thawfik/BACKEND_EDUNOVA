package com.studyplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.dto.course.CourseRequest;
import com.studyplatform.dto.course.CourseResponse;
import com.studyplatform.dto.course.CourseSummaryResponse;
import com.studyplatform.entity.Course;
import com.studyplatform.entity.User;
import com.studyplatform.enums.AccountType;
import com.studyplatform.enums.CourseStatus;
import com.studyplatform.exception.ApiException;
import com.studyplatform.repository.CourseRepository;
import com.studyplatform.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseService {

    private final CourseRepository courseRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif", "image/svg+xml",
            "video/mp4", "video/webm", "video/ogg", "video/quicktime"
    );
    private static final long MAX_MEDIA_SIZE = 50 * 1024 * 1024; // 50MB

    // ── Teacher operations ────────────────────────────────────

    @Transactional
    public CourseResponse create(CourseRequest request, User author) {
        requireTeacher(author);
        Course course = Course.builder()
                .author(author)
                .title(request.getTitle())
                .summary(request.getSummary())
                .domain(request.getDomain())
                .level(request.getLevel())
                .coverImageUrl(request.getCoverImageUrl())
                .content(writeContent(request.getContent()))
                .status(CourseStatus.DRAFT)
                .build();
        course = courseRepository.save(course);
        log.info("Course created: {} by {}", course.getTitle(), author.getEmail());
        return toResponse(course);
    }

    @Transactional
    public CourseResponse update(UUID id, CourseRequest request, User author) {
        Course course = findOwned(id, author);
        course.setTitle(request.getTitle());
        course.setSummary(request.getSummary());
        course.setDomain(request.getDomain());
        course.setLevel(request.getLevel());
        course.setCoverImageUrl(request.getCoverImageUrl());
        course.setContent(writeContent(request.getContent()));
        course = courseRepository.save(course);
        return toResponse(course);
    }

    @Transactional
    public CourseResponse publish(UUID id, User author) {
        Course course = findOwned(id, author);
        if (course.getSlug() == null || course.getSlug().isBlank()) {
            course.setSlug(generateUniqueSlug(course.getTitle()));
        }
        course.setStatus(CourseStatus.PUBLISHED);
        if (course.getPublishedAt() == null) {
            course.setPublishedAt(Instant.now());
        }
        course = courseRepository.save(course);
        log.info("Course published: {} ({})", course.getTitle(), course.getSlug());
        return toResponse(course);
    }

    @Transactional
    public CourseResponse unpublish(UUID id, User author) {
        Course course = findOwned(id, author);
        course.setStatus(CourseStatus.DRAFT);
        return toResponse(courseRepository.save(course));
    }

    @Transactional
    public void delete(UUID id, User author) {
        Course course = findOwned(id, author);
        courseRepository.delete(course);
    }

    public List<CourseSummaryResponse> listMine(User author) {
        return courseRepository.findByAuthorOrderByUpdatedAtDesc(author)
                .stream().map(this::toSummary).toList();
    }

    public CourseResponse getOwned(UUID id, User author) {
        return toResponse(findOwned(id, author));
    }

    @Transactional
    public String uploadMedia(MultipartFile file, User author) {
        requireTeacher(author);
        if (file.isEmpty()) {
            throw ApiException.badRequest("File is empty");
        }
        if (file.getSize() > MAX_MEDIA_SIZE) {
            throw ApiException.badRequest("File size exceeds 50MB limit");
        }
        if (file.getContentType() == null || !ALLOWED_MEDIA_TYPES.contains(file.getContentType())) {
            throw ApiException.badRequest("Unsupported media. Allowed: images (jpg, png, webp, gif, svg) and videos (mp4, webm, ogg, mov)");
        }
        String key = "courses/" + author.getId() + "/" + UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());
        storageService.store(file, key);
        return "/api/public/media/" + key;
    }

    // ── Public operations ─────────────────────────────────────

    public List<CourseSummaryResponse> listPublished(String domain, int limit) {
        Pageable pageable = PageRequest.of(0, Math.max(1, Math.min(limit, 100)));
        List<Course> courses = (domain != null && !domain.isBlank())
                ? courseRepository.findByStatusAndDomainOrderByPublishedAtDesc(CourseStatus.PUBLISHED, domain, pageable)
                : courseRepository.findByStatusOrderByPublishedAtDesc(CourseStatus.PUBLISHED, pageable);
        return courses.stream().map(this::toSummary).toList();
    }

    @Transactional
    public CourseResponse getPublishedBySlug(String slug) {
        Course course = courseRepository.findBySlugAndStatus(slug, CourseStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("Course not found"));
        course.setViewCount(course.getViewCount() + 1);
        courseRepository.save(course);
        return toResponse(course);
    }

    // ── Helpers ───────────────────────────────────────────────

    private Course findOwned(UUID id, User author) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Course not found"));
        if (!course.getAuthor().getId().equals(author.getId())) {
            throw ApiException.forbidden("You are not the author of this course");
        }
        return course;
    }

    private void requireTeacher(User user) {
        if (user.getAccountType() != AccountType.TEACHER) {
            throw ApiException.forbidden("Only teachers can publish courses");
        }
    }

    private String writeContent(JsonNode content) {
        if (content == null || content.isNull()) {
            return "{\"chapters\":[]}";
        }
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid course content");
        }
    }

    private JsonNode readContent(String content) {
        if (content == null || content.isBlank()) {
            return objectMapper.createObjectNode().set("chapters", objectMapper.createArrayNode());
        }
        try {
            return objectMapper.readTree(content);
        } catch (Exception e) {
            return objectMapper.createObjectNode().set("chapters", objectMapper.createArrayNode());
        }
    }

    private int chapterCount(String content) {
        JsonNode node = readContent(content);
        JsonNode chapters = node.get("chapters");
        return chapters != null && chapters.isArray() ? chapters.size() : 0;
    }

    private String generateUniqueSlug(String title) {
        String base = Normalizer.normalize(title == null ? "cours" : title, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) base = "cours";
        String slug = base;
        while (courseRepository.existsBySlug(slug)) {
            slug = base + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return slug;
    }

    private String sanitize(String filename) {
        if (filename == null) return "media";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private CourseResponse toResponse(Course c) {
        return CourseResponse.builder()
                .id(c.getId())
                .title(c.getTitle())
                .slug(c.getSlug())
                .summary(c.getSummary())
                .domain(c.getDomain())
                .level(c.getLevel())
                .coverImageUrl(c.getCoverImageUrl())
                .content(readContent(c.getContent()))
                .status(c.getStatus())
                .viewCount(c.getViewCount())
                .authorId(c.getAuthor().getId())
                .authorName(c.getAuthor().getFullName())
                .publishedAt(c.getPublishedAt())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private CourseSummaryResponse toSummary(Course c) {
        return CourseSummaryResponse.builder()
                .id(c.getId())
                .title(c.getTitle())
                .slug(c.getSlug())
                .summary(c.getSummary())
                .domain(c.getDomain())
                .level(c.getLevel())
                .coverImageUrl(c.getCoverImageUrl())
                .status(c.getStatus())
                .viewCount(c.getViewCount())
                .chapterCount(chapterCount(c.getContent()))
                .authorName(c.getAuthor().getFullName())
                .publishedAt(c.getPublishedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
