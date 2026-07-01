package com.studyplatform.ai;

import com.studyplatform.enums.DetailLevel;
import com.studyplatform.enums.Difficulty;
import com.studyplatform.enums.ExpertiseLevel;
import com.studyplatform.enums.QuizType;
import org.springframework.stereotype.Component;

/**
 * Central repository of all prompt templates for AI features.
 * Each method returns a system prompt and builds the user message
 * from the caller's parameters.
 */
@Component
public class PromptBuilder {

    // ══════════════════════════════════════════════════════════
    // STUDY GUIDE GENERATION
    // ══════════════════════════════════════════════════════════

    public static final String GUIDE_SYSTEM_PROMPT = """
            You are an expert educational content creator. Generate a rich, well-structured study
            guide tailored to the student's expertise level. Write in the SAME LANGUAGE as the topic
            (French topic -> French guide).

            Your output must be valid JSON with this exact structure:
            {
              "title": "Engaging guide title",
              "modules": [
                {
                  "title": "Clear module title (this is the big chapter heading)",
                  "type": "INTRODUCTION | CONCEPT | EXAMPLE | EXERCISE | SUMMARY",
                  "estimatedMinutes": 12,
                  "content": "Module body in clean Markdown",
                  "keyConceptBox": "One-sentence key takeaway or null"
                }
              ],
              "totalEstimatedMinutes": 75,
              "suggestedQuizTopics": ["topic1", "topic2", "topic3"]
            }

            STRUCTURE RULES:
            - Generate between 6 and 9 modules (NEVER only 4) that progressively build mastery.
              A good progression: 1 Introduction, 3-5 Concept modules (each covering ONE sub-theme
              in depth), 1-2 worked Examples/Exercises, 1 Summary.
            - Each module's "title" is the big chapter heading. Inside "content", organise the text
              with Markdown SUB-HEADINGS so there are clear titles AND sub-titles:
                * Use "## Section" for the main sections of the module.
                * Use "### Sub-section" for finer breakdowns.
            - Each module content: 250 to 500 words of genuinely useful explanation.

            MARKDOWN FORMATTING — produce clean Markdown that renders well:
            - Separate every block (heading, paragraph, list, table, quote, code) with a BLANK LINE.
            - Headings on their own line: "## Title", "### Subtitle".
            - Bullet lists: each item on its own line starting with "- ".
            - Numbered lists: each item on its own line starting with "1. ".
            - Bold with **text**, inline code with `code`.
            - Use fenced code blocks (```lang ... ```) for multi-line code, on their own lines.
            - Use a callout via "> " blockquote for an important note.
            - Use Markdown tables only when comparing things, with a proper header separator row.
            - Do NOT cram everything onto one line — real line breaks between blocks are mandatory.

            OTHER RULES:
            - Adapt vocabulary and depth to the expertise level.
            - Return ONLY valid JSON — no preamble, no markdown fences around the JSON, no trailing text.
            - Escape newlines inside JSON string values as \\n.
            """;

    public String buildGuideUserMessage(String topic, ExpertiseLevel level,
                                         String specificConcept, String documentText) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate a study guide on: ").append(topic).append("\n");
        sb.append("Expertise level: ").append(formatLevel(level)).append("\n");

        if (specificConcept != null && !specificConcept.isBlank()) {
            sb.append("Specific focus: ").append(specificConcept).append("\n");
        } else {
            sb.append("Scope: Broad overview of the topic\n");
        }

        if (documentText != null && !documentText.isBlank()) {
            sb.append("\nBase the guide on the following source material:\n");
            sb.append("---BEGIN DOCUMENT---\n");
            sb.append(documentText);
            sb.append("\n---END DOCUMENT---\n");
            sb.append("\nUse this document as the primary source. ");
            sb.append("Structure and explain its content according to the requested level.");
        }

        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════
    // STUDY GUIDE TRANSLATION
    // ══════════════════════════════════════════════════════════

    public static final String TRANSLATION_SYSTEM_PROMPT = """
            You are a professional translator specialised in educational content.
            You receive a JSON object describing a study guide and translate it into a target language.

            STRICT RULES — follow exactly:
            - Translate ONLY the human-readable string values: "title", each module "title",
              each module "content", each module "keyConceptBox", and the items of
              "suggestedQuizTopics".
            - Keep the JSON structure and ALL keys EXACTLY the same (do not add or remove fields).
            - Keep enum-like values unchanged (e.g. "type": "INTRODUCTION" stays "INTRODUCTION").
            - Preserve EVERY Markdown marker and layout: #, ##, ###, **bold**, *italic*, "- ", "1. ",
              "> ", tables with | and |---|, fenced ```code``` blocks, and all line breaks (\\n).
            - Do NOT translate code, commands, identifiers, or anything inside backticks or code fences.
            - Keep widely-used technical terms that are normally left untranslated.
            - Return ONLY the translated JSON — no preamble, no markdown fences, no trailing text.
            """;

    public String buildTranslationUserMessage(String guideJson, String targetLanguageName) {
        return "Translate the following study guide JSON into " + targetLanguageName
                + ". Return the SAME JSON structure with the values translated:\n\n" + guideJson;
    }

    // ══════════════════════════════════════════════════════════
    // QUIZ GENERATION
    // ══════════════════════════════════════════════════════════

    public static final String QUIZ_SYSTEM_PROMPT = """
            You are an expert assessment creator. You generate quiz questions that test 
            understanding, not just memorization.
            
            Your output must be valid JSON with this exact structure:
            {
              "title": "Quiz title",
              "questions": [
                {
                  "questionText": "The question",
                  "type": "MCQ | TRUE_FALSE | FREE_TEXT",
                  "options": ["A) ...", "B) ...", "C) ...", "D) ..."],
                  "correctAnswer": "A",
                  "explanation": "Why this is correct",
                  "difficulty": "EASY | MEDIUM | HARD",
                  "relatedConcept": "The concept being tested",
                  "points": 10
                }
              ]
            }
            
            Rules:
            - For MCQ: always provide exactly 4 options labeled A, B, C, D
            - For TRUE_FALSE: options are ["True", "False"], correctAnswer is "True" or "False"
            - For FREE_TEXT: options is null, correctAnswer is the expected answer
            - Explanations should teach, not just state the answer
            - Include code snippets in questions when testing programming concepts
            - Vary difficulty within the quiz (mix easy, medium, hard)
            - Return ONLY valid JSON, no preamble, no markdown fences
            """;

    public String buildQuizUserMessage(String topic, Difficulty difficulty,
                                        QuizType quizType, int questionCount,
                                        String guideContent, String themes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate a ").append(difficulty.name().toLowerCase());
        sb.append(" quiz on: ").append(topic).append("\n");
        sb.append("Number of questions: ").append(questionCount).append("\n");
        sb.append("Quiz type: ").append(formatQuizType(quizType)).append("\n");
        // Both quiz types must be auto-gradable: only MCQ and TRUE_FALSE allowed.
        sb.append("Use ONLY these question types, mixed together: MCQ and TRUE_FALSE. ")
          .append("Do NOT generate FREE_TEXT or open-ended questions.\n");

        if (quizType == QuizType.TOPIC_BASED && themes != null && !themes.isBlank()) {
            sb.append("Focus the questions specifically and exclusively on these themes: ")
              .append(themes).append(".\n");
            sb.append("Distribute the questions across these themes; do not ask about anything outside them.\n");
        }

        if (guideContent != null && !guideContent.isBlank()) {
            sb.append("\nBase questions on this study material:\n");
            sb.append("---BEGIN CONTENT---\n");
            sb.append(guideContent);
            sb.append("\n---END CONTENT---\n");
            sb.append("\nOnly ask about concepts covered in this content.");
        }

        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════
    // CONCEPT EXPLANATION
    // ══════════════════════════════════════════════════════════

    public static final String EXPLANATION_SYSTEM_PROMPT = """
            You are a patient, expert tutor. You explain concepts clearly and thoroughly,
            adapting to the requested level of detail.

            Your output must be valid JSON with this exact structure:
            {
              "concept": "The concept name",
              "definition": "One-sentence definition",
              "analogy": "A plain-language analogy using everyday objects",
              "explanation": "Explanation in Markdown (max 200 words)",
              "codeExamples": [
                {
                  "language": "python",
                  "code": "short code snippet",
                  "explanation": "One sentence"
                }
              ],
              "whenToUse": "One to two sentences",
              "commonMistakes": ["mistake1", "mistake2"],
              "relatedConcepts": ["concept1", "concept2"]
            }

            STRICT RULES — follow exactly:
            - definition: max 2 sentences
            - analogy: one sentence, everyday object comparison
            - explanation: max 200 words in Markdown
            - codeExamples: at most 1 example (SHORT level) or 2 (MEDIUM/DETAILED), null if non-technical topic
            - code snippets: max 15 lines each
            - commonMistakes: max 3 items, null for SHORT level
            - relatedConcepts: exactly 3 items
            - Keep the ENTIRE response under 1500 tokens
            - Return ONLY valid JSON — no preamble, no markdown fences, no trailing text
            """;

    public String buildExplanationUserMessage(String concept, DetailLevel level) {
        return String.format(
                "Explain the following concept: %s\nDetail level: %s\n",
                concept, formatDetailLevel(level));
    }

    // ══════════════════════════════════════════════════════════
    // RECOMMENDATIONS (for solo users)
    // ══════════════════════════════════════════════════════════

    public static final String RECOMMENDATION_SYSTEM_PROMPT = """
            You are a learning advisor. Based on a student's learning history, 
            you suggest specific topics and sub-topics they should study next.
            
            Your output must be valid JSON with this exact structure:
            {
              "recommendations": [
                {
                  "title": "Short title (e.g. 'Inheritance in Java')",
                  "description": "2-3 sentences explaining why this is recommended",
                  "reason": "WEAK_AREA | NATURAL_PROGRESSION | COMPLEMENTARY | UNEXPLORED",
                  "relatedTopic": "The parent topic this relates to",
                  "suggestedAction": "GENERATE_GUIDE | TAKE_QUIZ | EXPLAIN_CONCEPT"
                }
              ]
            }
            
            Rules:
            - Generate exactly 3-5 recommendations
            - Mix different reason types
            - Be specific (not "learn more Java" but "Java Collections Framework")
            - Prioritize weak areas and natural progressions
            - Return ONLY valid JSON, no preamble, no markdown fences
            """;

    public String buildRecommendationUserMessage(String learningProfile) {
        return "Based on this student's learning history, suggest what they should study next:\n\n"
                + learningProfile;
    }

    // ══════════════════════════════════════════════════════════
    // DOCUMENT SUMMARY
    // ══════════════════════════════════════════════════════════

    public static final String SUMMARY_SYSTEM_PROMPT = """
            You are a document summarizer. You create brief, useful summaries of 
            academic documents.
            
            Your output must be valid JSON:
            {
              "title": "Inferred document title",
              "summary": "2-3 paragraph summary",
              "mainTopics": ["topic1", "topic2", "topic3"],
              "suggestedStudyTopics": ["specific topic to generate a guide on"]
            }
            
            Rules:
            - Summary should be 2-3 paragraphs, factual, no fluff
            - mainTopics: 3-5 high-level topics covered
            - suggestedStudyTopics: 2-3 specific topics the student could generate guides for
            - Return ONLY valid JSON, no preamble, no markdown fences
            """;

    public String buildSummaryUserMessage(String documentText) {
        return "Summarize the following document:\n\n"
                + "---BEGIN DOCUMENT---\n"
                + documentText
                + "\n---END DOCUMENT---";
    }

    // ══════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════

    private String formatLevel(ExpertiseLevel level) {
        return switch (level) {
            case BEGINNER -> "Beginner — no prior knowledge, define every term, use simple analogies";
            case INTERMEDIATE -> "Intermediate — knows basics, go deeper, explain the why not just how";
            case PROFESSIONAL -> "Advanced — strong foundation, cover edge cases, design patterns, best practices";
        };
    }

    private String formatDetailLevel(DetailLevel level) {
        return switch (level) {
            case SHORT -> "Simple — explain like I've never heard of this, no jargon, everyday analogies";
            case MEDIUM -> "Intermediate — proper technical depth with definitions for key terms";
            case DETAILED -> "Advanced — full technical details, edge cases, nuances, multiple code examples";
        };
    }

    private String formatQuizType(QuizType type) {
        return switch (type) {
            case STANDARD -> "Standard — a balanced mix of multiple-choice (MCQ) and true/false questions";
            case CUSTOM -> "Standard — a balanced mix of multiple-choice (MCQ) and true/false questions";
            case TOPIC_BASED -> "Theme-focused — MCQ and true/false questions strictly limited to the specified themes";
        };
    }
}
