package com.battleiq.battleiq.game;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "game_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "room_code", nullable = false, length = 6)
    private String roomCode;

    @Column(name = "round_number")
    private int roundNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty")
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private GameStatus status;

    @Column(name = "questions", columnDefinition = "TEXT")
    private String questions;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    public enum Difficulty {
        MEDIUM,
        HARD,
        VERY_HARD
    }

    public enum GameStatus {
        QUESTION_IN_PROGRESS,
        WAITING_TIE_RESPONSE,
        ENDED
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionDTO {
        private String questionId;
        private int questionNumber;
        private int totalQuestions;
        private String question;
        private List<String> options;
        private int timerSeconds;
        private int roundNumber;
        private String difficulty;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerSubmitDTO {
        private String roomCode;
        private String playerName;
        private String questionId;
        private String selectedOption;
        private long timeTakenMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionResultDTO {
        private String questionId;
        private String correctAnswer;
        private List<PlayerAnswerResult> playerResults;
        private Map<String, Integer> currentScores;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlayerAnswerResult {
        private String playerName;
        private String selectedOption;
        @JsonProperty("isCorrect")
        private boolean isCorrect;
        @JsonProperty("isTimeout")
        private boolean isTimeout;
        private int pointsEarned;
        private long timeTakenMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FinalResultDTO {
        private List<PlayerFinalScore> leaderboard;
        private String winnerName;
        @JsonProperty("isTie")
        private boolean isTie;
        private String topicData;
        private Map<String, String> roasts;
        private int roundsPlayed;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlayerFinalScore {
        private int rank;
        private String playerName;
        private int totalScore;
        private int correctAnswers;
        private int totalQuestions;
        private double avgResponseTimeMs;
        @JsonProperty("isWinner")
        private boolean isWinner;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TieResponseDTO {
        private String roomCode;
        private String playerName;
        private boolean wantsRematch;
    }
}
