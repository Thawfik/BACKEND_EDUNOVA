package com.studyplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.ai.AiService;
import com.studyplatform.dto.quiz.*;
import com.studyplatform.entity.Quiz;
import com.studyplatform.entity.QuizAttempt;
import com.studyplatform.entity.StudyGuide;
import com.studyplatform.entity.User;
import com.studyplatform.enums.Difficulty;
import com.studyplatform.enums.ExpertiseLevel;
import com.studyplatform.enums.QuizType;
import com.studyplatform.exception.ApiException;
import com.studyplatform.repository.QuizAttemptRepository;
import com.studyplatform.repository.QuizRepository;
import com.studyplatform.repository.StudyGuideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final StudyGuideRepository guideRepository;
    private final AiService aiService;
    private final XpService xpService;
    private final ObjectMapper objectMapper;
    private final BadgeService badgeService;

    @Transactional
    public QuizResponse generate(User user, GenerateQuizRequest request) {
        StudyGuide guide = null;
        String guideContent = null;

        if (request.getGuideId() != null) {
            guide = guideRepository.findById(request.getGuideId())
                    .orElseThrow(() -> ApiException.notFound("Guide not found"));
            guideContent = guide.getContent();
        }

        log.info("Generating quiz for user {} — topic: {}, difficulty: {}, count: {}",
                user.getEmail(), request.getTopic(), request.getDifficulty(), request.getQuestionCount());

        JsonNode quizJson = aiService.generateQuiz(
                request.getTopic(),
                request.getDifficulty(),
                request.getQuizType(),
                request.getQuestionCount(),
                guideContent,
                request.getThemes());

        String title = quizJson.has("title")
                ? quizJson.get("title").asText()
                : request.getTopic() + " Quiz";

        int actualCount = quizJson.has("questions")
                ? quizJson.get("questions").size()
                : request.getQuestionCount();

        Quiz quiz = Quiz.builder()
                .user(user)
                .guide(guide)
                .title(title)
                .topic(request.getTopic())
                .difficulty(request.getDifficulty())
                .quizType(request.getQuizType())
                .questionCount(actualCount)
                .questions(quizJson.toString())
                .build();

        quiz = quizRepository.save(quiz);
        log.info("Quiz saved: {} ({})", quiz.getTitle(), quiz.getId());

        return toFullResponse(quiz);
    }

    /**
     * Generate a quiz directly from a study guide. Topic, difficulty, type and
     * question count are derived from the guide itself — the user is not asked to
     * configure anything. The quiz is built on the guide's content so the
     * questions always cover what the guide teaches.
     */
    @Transactional
    public QuizResponse generateFromGuide(User user, UUID guideId) {
        StudyGuide guide = guideRepository.findById(guideId)
                .orElseThrow(() -> ApiException.notFound("Guide not found"));
        if (!guide.getUser().getId().equals(user.getId())) {
            throw ApiException.forbidden("You do not have access to this guide");
        }

        Difficulty difficulty = mapExpertiseToDifficulty(guide.getExpertiseLevel());
        int questionCount = defaultQuestionCount(difficulty);
        String topic = guide.getTitle();

        log.info("Generating guide-based quiz for user {} — guide: {}, difficulty: {}, count: {}",
                user.getEmail(), guide.getId(), difficulty, questionCount);

        JsonNode quizJson = aiService.generateQuiz(
                topic,
                difficulty,
                QuizType.STANDARD,
                questionCount,
                guide.getContent(),
                null);

        String title = quizJson.has("title")
                ? quizJson.get("title").asText()
                : "Quiz — " + topic;

        int actualCount = quizJson.has("questions")
                ? quizJson.get("questions").size()
                : questionCount;

        Quiz quiz = Quiz.builder()
                .user(user)
                .guide(guide)
                .title(title)
                .topic(topic)
                .difficulty(difficulty)
                .quizType(QuizType.STANDARD)
                .questionCount(actualCount)
                .questions(quizJson.toString())
                .build();

        quiz = quizRepository.save(quiz);
        log.info("Guide-based quiz saved: {} ({})", quiz.getTitle(), quiz.getId());

        return toFullResponse(quiz);
    }

    private Difficulty mapExpertiseToDifficulty(ExpertiseLevel level) {
        if (level == null) return Difficulty.MEDIUM;
        return switch (level) {
            case BEGINNER -> Difficulty.EASY;
            case INTERMEDIATE -> Difficulty.MEDIUM;
            case PROFESSIONAL -> Difficulty.HARD;
        };
    }

    private int defaultQuestionCount(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 15;
            case MEDIUM -> 20;
            case HARD -> 30;
        };
    }

    @Transactional
    public QuizAttemptResponse submit(User user, SubmitQuizRequest request) {
        Quiz quiz = quizRepository.findById(request.getQuizId())
                .orElseThrow(() -> ApiException.notFound("Quiz not found"));

        JsonNode questionsNode;
        try {
            JsonNode root = objectMapper.readTree(quiz.getQuestions());
            questionsNode = root.has("questions") ? root.get("questions") : root;
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid quiz data");
        }

        int correctCount = 0;
        int totalPoints = 0;
        int earnedPoints = 0;

        for (SubmitQuizRequest.AnswerSubmission answer : request.getAnswers()) {
            if (answer.getQuestionIndex() >= questionsNode.size()) continue;

            JsonNode question = questionsNode.get(answer.getQuestionIndex());
            int points = question.has("points") ? question.get("points").asInt() : 10;
            totalPoints += points;

            String correctAnswer = question.has("correctAnswer")
                    ? question.get("correctAnswer").asText() : "";

            if (correctAnswer.equalsIgnoreCase(answer.getSelectedAnswer())) {
                correctCount++;
                earnedPoints += points;
            }
        }

        if (totalPoints == 0) totalPoints = 1;

        QuizAttempt attempt = QuizAttempt.builder()
                .user(user)
                .quiz(quiz)
                .score(earnedPoints)
                .totalPoints(totalPoints)
                .correctCount(correctCount)
                .totalQuestions(questionsNode.size())
                .answers(objectMapper.valueToTree(request.getAnswers()).toString())
                .timeTakenSeconds(request.getTimeTakenSeconds())
                .build();

        attempt = attemptRepository.save(attempt);

        // Award XP
        UUID topicId = quiz.getGuide() != null && quiz.getGuide().getTopic() != null
                ? quiz.getGuide().getTopic().getId() : null;

        xpService.awardXp(user, topicId, XpService.XP_QUIZ_COMPLETED,
                "QUIZ_COMPLETED", quiz.getId().toString());
        badgeService.checkAndAwardBadges(user);

        if (correctCount == questionsNode.size()) {
            xpService.awardXp(user, topicId, XpService.XP_QUIZ_PERFECT,
                    "QUIZ_PERFECT_SCORE", quiz.getId().toString());
        }

        log.info("Quiz attempt: {} scored {}/{} ({}%)", user.getEmail(),
                earnedPoints, totalPoints, (earnedPoints * 100 / totalPoints));

        return toAttemptResponse(attempt);
    }

    public List<QuizListResponse> listByUser(UUID userId) {
        Map<UUID, Double> bestScores = attemptRepository.findBestScoresByUserId(userId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> Math.round(((Number) row[1]).doubleValue() * 10.0) / 10.0));
        return quizRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(q -> toListResponse(q, bestScores.get(q.getId())))
                .toList();
    }

    public QuizResponse getById(UUID quizId, UUID userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> ApiException.notFound("Quiz not found"));
        if (!quiz.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("You do not have access to this quiz");
        }
        return toFullResponse(quiz);
    }

    public List<QuizAttemptResponse> getAttempts(UUID quizId, UUID userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> ApiException.notFound("Quiz not found"));
        if (!quiz.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("You do not have access to this quiz");
        }
        return attemptRepository.findByQuizIdOrderByCompletedAtDesc(quizId).stream()
                .map(this::toAttemptResponse)
                .toList();
    }

    public List<QuizAttemptResponse> getMyAttempts(UUID userId) {
        return attemptRepository.findByUserIdOrderByCompletedAtDesc(userId).stream()
                .map(this::toAttemptResponse)
                .toList();
    }

    @Transactional
    public void delete(UUID quizId, UUID userId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> ApiException.notFound("Quiz not found"));
        if (!quiz.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("You do not have access to this quiz");
        }
        quizRepository.delete(quiz);
    }

    private QuizResponse toFullResponse(Quiz quiz) {
        JsonNode questionsNode;
        try {
            JsonNode raw = objectMapper.readTree(quiz.getQuestions());
            questionsNode = raw.has("questions") ? raw.get("questions") : raw;
        }
        catch (Exception e) { questionsNode = objectMapper.createArrayNode(); }

        return QuizResponse.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .topic(quiz.getTopic())
                .difficulty(quiz.getDifficulty().name())
                .quizType(quiz.getQuizType().name())
                .questionCount(quiz.getQuestionCount())
                .questions(questionsNode)
                .guideId(quiz.getGuide() != null ? quiz.getGuide().getId() : null)
                .createdAt(quiz.getCreatedAt())
                .build();
    }

    private QuizListResponse toListResponse(Quiz quiz, Double bestScore) {
        return QuizListResponse.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .topic(quiz.getTopic())
                .difficulty(quiz.getDifficulty().name())
                .quizType(quiz.getQuizType().name())
                .questionCount(quiz.getQuestionCount())
                .createdAt(quiz.getCreatedAt())
                .bestScore(bestScore)
                .build();
    }

    private QuizAttemptResponse toAttemptResponse(QuizAttempt attempt) {
        JsonNode answersNode;
        try { answersNode = objectMapper.readTree(attempt.getAnswers()); }
        catch (Exception e) { answersNode = objectMapper.createObjectNode(); }

        double pct = attempt.getTotalPoints() > 0
                ? (attempt.getScore() * 100.0 / attempt.getTotalPoints()) : 0;

        return QuizAttemptResponse.builder()
                .id(attempt.getId())
                .quizId(attempt.getQuiz().getId())
                .quizTitle(attempt.getQuiz().getTitle())
                .score(attempt.getScore())
                .totalPoints(attempt.getTotalPoints())
                .correctCount(attempt.getCorrectCount())
                .totalQuestions(attempt.getTotalQuestions())
                .percentageScore(Math.round(pct * 10.0) / 10.0)
                .answers(answersNode)
                .timeTakenSeconds(attempt.getTimeTakenSeconds())
                .completedAt(attempt.getCompletedAt())
                .build();
    }
}
