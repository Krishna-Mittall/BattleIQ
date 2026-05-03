package com.battleiq.battleiq.ai;

import com.battleiq.battleiq.game.GameSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.base.url}")
    private String groqBaseUrl;

    @Value("${groq.model}")
    private String groqModel;

    public List<Map<String, Object>> generateQuestions(String topicData, String difficulty, int count) {
        try {
            String prompt = buildQuestionPrompt(topicData, difficulty, count);
            String response = callGroq(prompt);
            return parseQuestions(response, count);
        } catch (Exception e) {
            log.error("Question generation failed: {}", e.getMessage());
            return getFallbackQuestions(count);
        }
    }

    public Map<String, String> generateRoasts(List<GameSession.PlayerFinalScore> leaderboard) {
        try {
            String prompt = buildRoastPrompt(leaderboard);
            String response = callGroq(prompt);
            return parseRoasts(response, leaderboard);
        } catch (Exception e) {
            log.error("Roast generation failed: {}", e.getMessage());
            return getFallbackRoasts(leaderboard);
        }
    }

    private String buildQuestionPrompt(String topicData, String difficulty, int count) throws Exception {
        JsonNode topic = objectMapper.readTree(topicData);
        String type = topic.path("type").asText("CUSTOM");

        String context = switch (type) {
            case "MOVIE" -> """
                    Movie: %s (%s)
                    Generate questions about plot, characters, dialogues, and key scenes.
                    Do NOT ask about behind-the-scenes, production, or box office.
                    """.formatted(
                    topic.path("title").asText(),
                    topic.path("year").asText()
            );
            case "SERIES" -> """
                    TV Series: %s
                    Scope: Season %s to Season %s, up to Episode %s of the last season.
                    STRICT RULE: Do NOT generate any questions about events that happen
                    after Season %s Episode %s. No spoilers beyond this point.
                    Generate questions about plot, characters, and events within this scope only.
                    """.formatted(
                    topic.path("title").asText(),
                    topic.path("seasonFrom").asText(),
                    topic.path("seasonTo").asText(),
                    topic.path("episodeUpto").asText(),
                    topic.path("seasonTo").asText(),
                    topic.path("episodeUpto").asText()
            );
            default -> "Topic: " + topic.path("normalizedTopic").asText() +
                    "\nGenerate general knowledge questions about this topic.";
        };

        String difficultyInstruction = switch (difficulty) {
            case "HARD" -> "Difficulty: HARD - Ask specific, deep, and challenging questions.";
            case "MEDIUM" -> "Difficulty: MEDIUM - Ask balanced questions with a mix of straightforward and detailed knowledge.";
            default -> "Difficulty: EASY - Ask accessible questions and avoid obscure details.";
        };

        return """
                You are a quiz question generator.

                %s

                %s

                Generate exactly %d multiple choice questions.

                STRICT RULES:
                - Each question must have exactly 4 options labeled A, B, C, D
                - Only one option is correct
                - Questions must be factually accurate
                - Use only stable, well-established facts
                - Avoid outdated news, live rankings, current prices, recent transfers, or fast-changing information
                - If the topic is time-sensitive and no year/season/episode cutoff is provided, prefer evergreen questions
                - Never guess; skip uncertain facts internally and produce only reliable questions
                - No duplicate questions
                - Do not reveal the answer in the question itself

                Respond ONLY with a valid JSON array. No markdown, no explanation.
                Format:
                [
                  {
                    "id": "q1",
                    "question": "Question text here?",
                    "options": ["A. Option one", "B. Option two", "C. Option three", "D. Option four"],
                    "correctAnswer": "A"
                  }
                ]
                """.formatted(context, difficultyInstruction, count);
    }

    private String buildRoastPrompt(List<GameSession.PlayerFinalScore> leaderboard) {
        StringBuilder playerInfo = new StringBuilder();
        for (GameSession.PlayerFinalScore player : leaderboard) {
            playerInfo.append("""
                    - %s: Rank #%d, Score %d, Accuracy %d/%d
                    """.formatted(
                    player.getPlayerName(),
                    player.getRank(),
                    player.getTotalScore(),
                    player.getCorrectAnswers(),
                    player.getTotalQuestions()
            ));
        }

        return """
                You are a funny quiz host generating personalized one-liner roasts for players.

                Players (in ranking order):
                %s

                Generate a short funny one-liner for each player based on their performance.

                Rules:
                - Winner gets a celebrating/teasing line
                - Middle players get a funny consolation line
                - Last place gets the funniest friendly roast
                - Keep it light, funny, and quiz-themed
                - Max 10 words per roast
                - Hinglish is fine

                Respond ONLY with valid JSON. No markdown, no explanation.
                Format:
                {
                  "PlayerName1": "Roast line here",
                  "PlayerName2": "Roast line here"
                }
                """.formatted(playerInfo);
    }

    private List<Map<String, Object>> parseQuestions(String response, int expectedCount) {
        try {
            String cleaned = cleanJsonResponse(response);
            List<Map<String, Object>> questions = objectMapper.readValue(
                    cleaned,
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            List<Map<String, Object>> valid = new ArrayList<>();
            for (Map<String, Object> question : questions) {
                Map<String, Object> normalized = normalizeQuestion(question);
                if (isValidQuestion(normalized)) {
                    valid.add(normalized);
                }
                if (valid.size() >= expectedCount) {
                    break;
                }
            }

            while (valid.size() < expectedCount) {
                valid.add(getFallbackQuestion(valid.size() + 1));
            }

            return valid;
        } catch (Exception e) {
            log.error("Failed to parse questions from AI response: {}", e.getMessage());
            return getFallbackQuestions(expectedCount);
        }
    }

    private Map<String, String> parseRoasts(String response, List<GameSession.PlayerFinalScore> leaderboard) {
        try {
            String cleaned = cleanJsonResponse(response);
            return objectMapper.readValue(cleaned, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.error("Failed to parse roasts from AI response: {}", e.getMessage());
            return getFallbackRoasts(leaderboard);
        }
    }

    private String callGroq(String userPrompt) throws Exception {
        String bodyJson = objectMapper.writeValueAsString(Map.of(
                "model", groqModel,
                "messages", List.of(Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.2,
                "max_tokens", 2000
        ));

        RequestBody body = RequestBody.create(bodyJson, MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(groqBaseUrl)
                .addHeader("Authorization", "Bearer " + groqApiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Groq API error: HTTP " + response.code());
            }
            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("choices").get(0).path("message").path("content").asText();
        }
    }

    private String cleanJsonResponse(String response) {
        return response
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();
    }

    private boolean isValidQuestion(Map<String, Object> question) {
        if (!question.containsKey("question") || !question.containsKey("options") || !question.containsKey("correctAnswer")) {
            return false;
        }
        Object options = question.get("options");
        if (!(options instanceof List<?>) || ((List<?>) options).size() != 4) {
            return false;
        }
        String answer = (String) question.get("correctAnswer");
        return answer != null && List.of("A", "B", "C", "D").contains(answer);
    }

    private Map<String, Object> normalizeQuestion(Map<String, Object> question) {
        Map<String, Object> normalized = new LinkedHashMap<>(question);
        Object optionsObject = normalized.get("options");
        if (!(optionsObject instanceof List<?> rawOptions) || rawOptions.size() != 4) {
            return normalized;
        }

        List<String> options = new ArrayList<>();
        for (Object option : rawOptions) {
            options.add(option == null ? "" : option.toString().trim());
        }

        String answer = normalized.get("correctAnswer") == null
                ? null
                : normalized.get("correctAnswer").toString().trim();

        String normalizedAnswer = normalizeCorrectAnswer(answer, options);
        normalized.put("options", options);
        normalized.put("correctAnswer", normalizedAnswer);
        return normalized;
    }

    private String normalizeCorrectAnswer(String answer, List<String> options) {
        if (answer == null || answer.isBlank()) {
            return null;
        }

        String upper = answer.trim().toUpperCase(Locale.ROOT);
        if (List.of("A", "B", "C", "D").contains(upper)) {
            return upper;
        }

        if (upper.matches("^[A-D][.)\\-:].*")) {
            return String.valueOf(upper.charAt(0));
        }

        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i).trim();
            if (option.equalsIgnoreCase(answer.trim())) {
                return String.valueOf((char) ('A' + i));
            }

            String optionBody = option.replaceFirst("^[A-D][.)\\-:]\\s*", "").trim();
            if (optionBody.equalsIgnoreCase(answer.trim())) {
                return String.valueOf((char) ('A' + i));
            }
        }

        return null;
    }

    private List<Map<String, Object>> getFallbackQuestions(int count) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            questions.add(getFallbackQuestion(i + 1));
        }
        return questions;
    }

    private Map<String, Object> getFallbackQuestion(int index) {
        return Map.of(
                "id", "fallback_q" + index,
                "question", "Which of the following is a programming language?",
                "options", List.of("A. Java", "B. HTML", "C. CSS", "D. JSON"),
                "correctAnswer", "A"
        );
    }

    private Map<String, String> getFallbackRoasts(List<GameSession.PlayerFinalScore> leaderboard) {
        Map<String, String> roasts = new LinkedHashMap<>();
        String[] lines = {
                "Dimag aur quiz dono mein gold medal!",
                "Ek aur round hota toh pakka jeet jaata!",
                "Next time preparation se aana bhai!",
                "Guess karna bhi ek talent hai!"
        };

        for (int i = 0; i < leaderboard.size(); i++) {
            roasts.put(
                    leaderboard.get(i).getPlayerName(),
                    lines[Math.min(i, lines.length - 1)]
            );
        }
        return roasts;
    }
}
