package com.battleiq.battleiq.topic;

import lombok.*;
import java.util.List;

public class TopicDTO {

    // ─── Search Results ──────────────────────────────────────────────────────

    // Single movie result from TMDB search
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MovieResult {
        private int tmdbId;
        private String title;
        private int year;           // Release year
        private String posterUrl;   // Full poster image URL
    }

    // Single series result from TMDB search
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SeriesResult {
        private int tmdbId;
        private String title;
        private int firstAirYear;
        private int totalSeasons;
        private String posterUrl;
    }

    // Season info — returned when host selects a series
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SeasonInfo {
        private int seasonNumber;
        private int episodeCount;
        private String name;        // "Season 1", "Season 2"
    }

    // ─── Custom Topic Validation ─────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomTopicRequest {
        private String input; // Raw text from host
    }

    // AI validation result for custom topic
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomTopicValidationResult {
        private ValidationStatus status;
        private String normalizedTopic;     // Clean version of input
        private String displayText;         // Shown in lobby/game
        private String message;             // Shown to user
        private List<String> suggestions;   // If AMBIGUOUS or TOO_BROAD

        public enum ValidationStatus {
            VALID,       // Good to go
            AMBIGUOUS,   // Multiple meanings — show suggestions
            TOO_BROAD,   // Too generic — suggest narrower topics
            INVALID      // Nonsense input — ask to try again
        }
    }

    // ─── Final Topic Data (stored as JSON in Room) ───────────────────────────

    // Movie topic
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MovieTopicData {
        private String type;        // "MOVIE"
        private int tmdbId;
        private String title;
        private int year;
        private String displayText; // "Avengers: Infinity War (2018)"
    }

    // Series topic
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SeriesTopicData {
        private String type;        // "SERIES"
        private int tmdbId;
        private String title;
        private int seasonFrom;
        private int seasonTo;
        private int episodeUpto;    // Episode limit in last season
        private String displayText; // "Game of Thrones S1-S3 (Ep 5)"
    }

    // Custom topic
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CustomTopicData {
        private String type;            // "CUSTOM"
        private String rawInput;        // Original user input
        private String normalizedTopic; // AI cleaned version
        private String displayText;     // Shown in lobby
    }
}