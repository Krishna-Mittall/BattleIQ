package com.battleiq.battleiq.topic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopicService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${tmdb.api.key}")
    private String tmdbApiKey;

    @Value("${tmdb.base.url}")
    private String tmdbBaseUrl;

    @Value("${groq.api.key}")
    private String groqApiKey;

    @Value("${groq.base.url}")
    private String groqBaseUrl;

    @Value("${groq.model}")
    private String groqModel;

    // ─── MOVIE SEARCH ────────────────────────────────────────────────────────

    // Search movies by name — returns top 5 results
    public List<TopicDTO.MovieResult> searchMovies(String query) {
        String url = tmdbBaseUrl + "/search/movie"
                + "?query=" + encodeQuery(query)
                + "&api_key=" + tmdbApiKey
                + "&language=en-US&page=1";

        try {
            String response = get(url);
            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.get("results");

            List<TopicDTO.MovieResult> movies = new ArrayList<>();
            int count = 0;

            for (JsonNode node : results) {
                if (count >= 5) break; // Return top 5 only

                String releaseDate = node.path("release_date").asText("");
                int year = releaseDate.length() >= 4
                        ? Integer.parseInt(releaseDate.substring(0, 4))
                        : 0;

                String posterPath = node.path("poster_path").asText(null);
                String posterUrl = (posterPath != null)
                        ? "https://image.tmdb.org/t/p/w200" + posterPath
                        : null;

                movies.add(TopicDTO.MovieResult.builder()
                        .tmdbId(node.get("id").asInt())
                        .title(node.get("title").asText())
                        .year(year)
                        .posterUrl(posterUrl)
                        .build());
                count++;
            }
            return movies;

        } catch (Exception e) {
            log.error("TMDB movie search failed for query '{}': {}", query, e.getMessage());
            return new ArrayList<>();
        }
    }

    // ─── SERIES SEARCH ───────────────────────────────────────────────────────

    // Search series by name — returns top 5 results with season count
    public List<TopicDTO.SeriesResult> searchSeries(String query) {
        String url = tmdbBaseUrl + "/search/tv"
                + "?query=" + encodeQuery(query)
                + "&api_key=" + tmdbApiKey
                + "&language=en-US&page=1";

        try {
            String response = get(url);
            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.get("results");

            List<TopicDTO.SeriesResult> seriesList = new ArrayList<>();
            int count = 0;

            for (JsonNode node : results) {
                if (count >= 5) break;

                String firstAirDate = node.path("first_air_date").asText("");
                int year = firstAirDate.length() >= 4
                        ? Integer.parseInt(firstAirDate.substring(0, 4))
                        : 0;

                String posterPath = node.path("poster_path").asText(null);
                String posterUrl = (posterPath != null)
                        ? "https://image.tmdb.org/t/p/w200" + posterPath
                        : null;

                seriesList.add(TopicDTO.SeriesResult.builder()
                        .tmdbId(node.get("id").asInt())
                        .title(node.get("name").asText())
                        .firstAirYear(year)
                        .totalSeasons(node.path("number_of_seasons").asInt(0))
                        .posterUrl(posterUrl)
                        .build());
                count++;
            }
            return seriesList;

        } catch (Exception e) {
            log.error("TMDB series search failed for query '{}': {}", query, e.getMessage());
            return new ArrayList<>();
        }
    }

    // ─── GET SERIES SEASONS ──────────────────────────────────────────────────

    // Returns all seasons with episode counts — used to build dynamic dropdowns
    public List<TopicDTO.SeasonInfo> getSeriesSeasons(int seriesId) {
        String url = tmdbBaseUrl + "/tv/" + seriesId
                + "?api_key=" + tmdbApiKey
                + "&language=en-US";

        try {
            String response = get(url);
            JsonNode root = objectMapper.readTree(response);
            JsonNode seasons = root.get("seasons");

            List<TopicDTO.SeasonInfo> result = new ArrayList<>();

            for (JsonNode season : seasons) {
                int seasonNumber = season.get("season_number").asInt();
                if (seasonNumber == 0) continue; // Skip "Specials" (season 0)

                result.add(TopicDTO.SeasonInfo.builder()
                        .seasonNumber(seasonNumber)
                        .episodeCount(season.get("episode_count").asInt())
                        .name(season.path("name").asText("Season " + seasonNumber))
                        .build());
            }
            return result;

        } catch (Exception e) {
            log.error("TMDB seasons fetch failed for series {}: {}", seriesId, e.getMessage());
            return new ArrayList<>();
        }
    }

    // ─── GET EPISODE COUNT FOR SPECIFIC SEASON ───────────────────────────────

    // Returns episode count for one season — used for episode dropdown
    public int getEpisodeCount(int seriesId, int seasonNumber) {
        String url = tmdbBaseUrl + "/tv/" + seriesId
                + "/season/" + seasonNumber
                + "?api_key=" + tmdbApiKey;

        try {
            String response = get(url);
            JsonNode root = objectMapper.readTree(response);
            JsonNode episodes = root.get("episodes");
            return (episodes != null) ? episodes.size() : 0;

        } catch (Exception e) {
            log.error("TMDB episode count failed for series {} season {}: {}", seriesId, seasonNumber, e.getMessage());
            return 0;
        }
    }

    // ─── VALIDATE CUSTOM TOPIC ───────────────────────────────────────────────

    // AI validates the custom topic entered by host
    public TopicDTO.CustomTopicValidationResult validateCustomTopic(String input) {

        // Basic checks before calling AI
        if (input == null || input.trim().isEmpty()) {
            return TopicDTO.CustomTopicValidationResult.builder()
                    .status(TopicDTO.CustomTopicValidationResult.ValidationStatus.INVALID)
                    .message("Topic cannot be empty")
                    .build();
        }

        String trimmed = input.trim();

        // Too short
        if (trimmed.length() < 3) {
            return TopicDTO.CustomTopicValidationResult.builder()
                    .status(TopicDTO.CustomTopicValidationResult.ValidationStatus.INVALID)
                    .message("Topic is too short. Try something more specific.")
                    .build();
        }

        // Call Groq AI for validation
        return callGroqForTopicValidation(trimmed);
    }

    // ─── GROQ AI — Topic Validation ──────────────────────────────────────────

    private TopicDTO.CustomTopicValidationResult callGroqForTopicValidation(String input) {
        String prompt = buildValidationPrompt(input);

        try {
            String groqResponse = callGroq(prompt);
            return parseValidationResponse(groqResponse, input);

        } catch (Exception e) {
            log.error("Groq topic validation failed for input '{}': {}", input, e.getMessage());

            // Fallback — accept as-is if AI fails
            return TopicDTO.CustomTopicValidationResult.builder()
                    .status(TopicDTO.CustomTopicValidationResult.ValidationStatus.VALID)
                    .normalizedTopic(input)
                    .displayText(input)
                    .message("Topic accepted")
                    .build();
        }
    }

    private String buildValidationPrompt(String input) {
        return """
                You are a quiz topic validator. Analyze this topic input: "%s"
                
                Respond ONLY with a valid JSON object (no markdown, no explanation):
                
                {
                  "status": "VALID" | "AMBIGUOUS" | "TOO_BROAD" | "INVALID",
                  "normalizedTopic": "cleaned version of the topic",
                  "displayText": "short label for lobby display",
                  "message": "one line message to show user",
                  "suggestions": ["suggestion1", "suggestion2"]
                }
                
                Rules:
                - VALID: clear, specific, quiz-worthy topic (Java Multithreading, IPL 2023, Breaking Bad)
                - AMBIGUOUS: multiple meanings exist (Avatar could be movie or cartoon)
                - TOO_BROAD: too generic to make good questions (just "movies" or "programming")
                - INVALID: nonsense, random characters, offensive content
                - suggestions: only for AMBIGUOUS (2-3 specific options) or TOO_BROAD (2-3 narrower topics)
                - For VALID and INVALID, suggestions should be empty array []
                """.formatted(input);
    }

    private TopicDTO.CustomTopicValidationResult parseValidationResponse(String response, String fallbackInput) {
        try {
            // Clean response — remove any markdown backticks if present
            String cleaned = response.replaceAll("```json", "").replaceAll("```", "").trim();
            JsonNode node = objectMapper.readTree(cleaned);

            String statusStr = node.path("status").asText("VALID");
            TopicDTO.CustomTopicValidationResult.ValidationStatus status =
                    TopicDTO.CustomTopicValidationResult.ValidationStatus.valueOf(statusStr);

            List<String> suggestions = new ArrayList<>();
            JsonNode suggestionsNode = node.get("suggestions");
            if (suggestionsNode != null && suggestionsNode.isArray()) {
                for (JsonNode s : suggestionsNode) {
                    suggestions.add(s.asText());
                }
            }

            return TopicDTO.CustomTopicValidationResult.builder()
                    .status(status)
                    .normalizedTopic(node.path("normalizedTopic").asText(fallbackInput))
                    .displayText(node.path("displayText").asText(fallbackInput))
                    .message(node.path("message").asText(""))
                    .suggestions(suggestions)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse Groq validation response: {}", e.getMessage());
            return TopicDTO.CustomTopicValidationResult.builder()
                    .status(TopicDTO.CustomTopicValidationResult.ValidationStatus.VALID)
                    .normalizedTopic(fallbackInput)
                    .displayText(fallbackInput)
                    .message("Topic accepted")
                    .suggestions(new ArrayList<>())
                    .build();
        }
    }

    // ─── HTTP Helpers ─────────────────────────────────────────────────────────

    // Simple GET request using OkHttp
    private String get(String url) throws Exception {
        Request request = new Request.Builder().url(url).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("HTTP " + response.code() + " from: " + url);
            }
            return response.body().string();
        }
    }

    // POST to Groq API
    private String callGroq(String userPrompt) throws Exception {
        String bodyJson = """
                {
                  "model": "%s",
                  "messages": [
                    {"role": "user", "content": %s}
                  ],
                  "temperature": 0.3
                }
                """.formatted(groqModel, objectMapper.writeValueAsString(userPrompt));

        RequestBody body = RequestBody.create(bodyJson, MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(groqBaseUrl)
                .addHeader("Authorization", "Bearer " + groqApiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Groq API error: " + response.code());
            }
            String responseBody = response.body().string();
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("choices").get(0).path("message").path("content").asText();
        }
    }

    // Encode query string for URL
    private String encodeQuery(String query) {
        return query.trim().replace(" ", "%20");
    }
}