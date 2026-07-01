package com.studyplatform.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.enums.DetailLevel;
import com.studyplatform.enums.Difficulty;
import com.studyplatform.enums.ExpertiseLevel;
import com.studyplatform.enums.QuizType;
import com.studyplatform.enums.TournamentQuestionType;
import com.studyplatform.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final ClaudeClient claudeClient;
    private final PromptBuilder promptBuilder;
    private final DocumentExtractor documentExtractor;
    private final ObjectMapper objectMapper;

    // ── Study Guide ───────────────────────────────────────────

    public JsonNode generateGuide(String topic, ExpertiseLevel level,
                                   String specificConcept, String documentStorageKey) {
        String documentText = null;
        if (documentStorageKey != null) {
            documentText = documentExtractor.extractText(documentStorageKey);
        }

        String userMessage = promptBuilder.buildGuideUserMessage(
                topic, level, specificConcept, documentText);

        String response = claudeClient.chat(
                PromptBuilder.GUIDE_SYSTEM_PROMPT, userMessage, 8000);

        return parseJson(response, "guide");
    }

    // ── Study Guide Translation ───────────────────────────────

    public JsonNode translateGuide(String guideContentJson, String targetLang) {
        String langName = switch (targetLang == null ? "" : targetLang.toLowerCase()) {
            case "fr", "french", "français", "francais" -> "French";
            case "en", "english", "anglais" -> "English";
            default -> targetLang;
        };

        String userMessage = promptBuilder.buildTranslationUserMessage(guideContentJson, langName);
        // A full guide (up to 9 modules) translated into a wordier language can be
        // large — give it plenty of room so the JSON is never truncated.
        String response = claudeClient.chat(
                PromptBuilder.TRANSLATION_SYSTEM_PROMPT, userMessage, 16000);

        return parseJson(response, "translation");
    }

    // ── Quiz ──────────────────────────────────────────────────

    public JsonNode generateQuiz(String topic, Difficulty difficulty,
                                  QuizType quizType, int questionCount,
                                  String guideContent, String themes) {
        String userMessage = promptBuilder.buildQuizUserMessage(
                topic, difficulty, quizType, questionCount, guideContent, themes);

        String response = claudeClient.chat(
                PromptBuilder.QUIZ_SYSTEM_PROMPT, userMessage, 8000);

        return parseJson(response, "quiz");
    }

    // ── Concept Explanation ───────────────────────────────────

    public JsonNode explainConcept(String concept, DetailLevel level) {
        String userMessage = promptBuilder.buildExplanationUserMessage(concept, level);

        String response = claudeClient.chat(
                PromptBuilder.EXPLANATION_SYSTEM_PROMPT, userMessage, 4000);

        return parseJson(response, "explanation");
    }

    // ── Recommendations ───────────────────────────────────────

    public JsonNode generateRecommendations(String learningProfile) {
        String userMessage = promptBuilder.buildRecommendationUserMessage(learningProfile);

        String response = claudeClient.chat(
                PromptBuilder.RECOMMENDATION_SYSTEM_PROMPT, userMessage, 2000);

        return parseJson(response, "recommendations");
    }

    // ── Document Summary (async — called after upload) ────────

    @Async
    public CompletableFuture<JsonNode> summarizeDocument(String storageKey) {
        try {
            String documentText = documentExtractor.extractPreview(storageKey, 20000);
            String userMessage = promptBuilder.buildSummaryUserMessage(documentText);

            String response = claudeClient.chat(
                    PromptBuilder.SUMMARY_SYSTEM_PROMPT, userMessage, 1500);

            return CompletableFuture.completedFuture(parseJson(response, "summary"));
        } catch (Exception e) {
            log.error("Async document summary failed for {}: {}", storageKey, e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    // ── Tournament Question Generation ────────────────────────

    public JsonNode generateTournamentQuestions(String topic, int count, String difficulty,
                                                 List<TournamentQuestionType> types) {
        String typesList = types.stream()
                .map(TournamentQuestionType::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("MCQ, TRUE_FALSE");

        String systemPrompt = """
                You are an expert educational assessment creator specializing in competitive tournaments and hackathons.
                Generate tournament questions in strict JSON format.

                Your output must be valid JSON with this EXACT structure:
                {
                  "questions": [
                    {
                      "type": "MCQ",
                      "content": {
                        "question": "...",
                        "options": [{"text": "...", "isCorrect": true}, {"text": "...", "isCorrect": false}],
                        "explanation": "..."
                      },
                      "points": 10,
                      "difficulty": "MEDIUM"
                    },
                    {
                      "type": "TRUE_FALSE",
                      "content": {
                        "statement": "...",
                        "isTrue": true,
                        "explanation": "..."
                      },
                      "points": 5,
                      "difficulty": "EASY"
                    },
                    {
                      "type": "CODE_IMAGE",
                      "content": {
                        "imageDescription": "...",
                        "codeSnippet": "...",
                        "language": "java",
                        "question": "What does this code do?",
                        "sampleAnswer": "..."
                      },
                      "points": 20,
                      "difficulty": "HARD"
                    },
                    {
                      "type": "ESSAY",
                      "content": {
                        "prompt": "...",
                        "hints": ["..."],
                        "suggestedKeywords": ["..."]
                      },
                      "points": 15,
                      "difficulty": "MEDIUM"
                    },
                    {
                      "type": "CODE_CHALLENGE",
                      "content": {
                        "description": "...",
                        "starterCode": "...",
                        "language": "java",
                        "expectedOutput": "...",
                        "testCases": [{"input": "...", "expected": "..."}]
                      },
                      "points": 25,
                      "difficulty": "HARD"
                    }
                  ]
                }

                STRICT RULES:
                - Return ONLY valid JSON — no preamble, no markdown fences, no trailing text
                - MCQ must have exactly 4 options with exactly 1 isCorrect=true
                - TRUE_FALSE must have boolean isTrue field
                - CODE_IMAGE must include a real "codeSnippet" (multi-line, 5-15 lines) plus a concise "sampleAnswer" describing what the code does
                - CODE_CHALLENGE must be solvable by reading input from STDIN and writing the result to STDOUT.
                  Provide 2-4 "testCases" where "input" is the exact stdin and "expected" is the exact stdout (trailing newline ignored).
                  "starterCode" must be minimal scaffolding in the given "language". Keep problems small and deterministic.
                  Prefer "language":"python". Supported languages: python, java, javascript, c, cpp.
                  If "language":"java", the public class MUST be named Main (Wandbox requirement).
                - Use ONLY the question types requested by the user
                - Vary difficulty levels appropriately
                - Make questions challenging but fair for a competitive tournament
                """;

        String userMessage = String.format(
                "Generate %d tournament questions on the topic: %s\n" +
                "Overall difficulty level: %s\n" +
                "Use these question types (mix them): %s\n" +
                "Make questions specific, educational, and appropriate for competitive assessment.",
                count, topic, difficulty, typesList);

        String response = claudeClient.chat(systemPrompt, userMessage, 8000);
        return parseJson(response, "tournament questions");
    }

    /**
     * Grade a candidate's free-text explanation of a code snippet (CODE_IMAGE).
     * Returns strict JSON: {@code {"correct": bool, "score": 0..1, "feedback": "..."}}.
     */
    public JsonNode gradeCodeExplanation(String codeSnippet, String sampleAnswer, String userAnswer) {
        String systemPrompt = """
                You are a strict but fair programming examiner grading a student's explanation of a code snippet.
                Judge whether the student correctly understood what the code does.

                Return ONLY valid JSON, no markdown, with this EXACT structure:
                { "correct": true, "score": 0.0, "feedback": "..." }

                RULES:
                - "score" is a number from 0.0 to 1.0 reflecting how complete and accurate the explanation is.
                - "correct" is true when score >= 0.6 (the student grasped the essential behavior).
                - Be tolerant of language/phrasing; focus on conceptual correctness, not wording.
                - "feedback" is one short sentence in French.
                """;

        String userMessage = String.format("""
                CODE:
                ```
                %s
                ```

                REFERENCE ANSWER (what a correct explanation should convey):
                %s

                STUDENT'S EXPLANATION:
                %s
                """, codeSnippet == null ? "" : codeSnippet,
                sampleAnswer == null ? "(none provided)" : sampleAnswer,
                userAnswer == null ? "" : userAnswer);

        String response = claudeClient.chat(systemPrompt, userMessage, 1000);
        return parseJson(response, "code explanation grading");
    }

    // ── JSON Parsing ──────────────────────────────────────────

    private JsonNode parseJson(String response, String context) {
        // Claude sometimes wraps JSON in markdown code fences
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        // If the model added any prose around the JSON, keep only the JSON object/array.
        if (!cleaned.isEmpty() && cleaned.charAt(0) != '{' && cleaned.charAt(0) != '[') {
            int objStart = cleaned.indexOf('{');
            int arrStart = cleaned.indexOf('[');
            int start = (objStart < 0) ? arrStart : (arrStart < 0 ? objStart : Math.min(objStart, arrStart));
            int end = Math.max(cleaned.lastIndexOf('}'), cleaned.lastIndexOf(']'));
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }
        }

        try {
            return objectMapper.readTree(cleaned);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Claude {} response as JSON: {}", context, e.getMessage());
            log.debug("Raw response: {}", response);
            throw ApiException.badRequest(
                    "AI returned an invalid response. Please try again.");
        }
    }
}
