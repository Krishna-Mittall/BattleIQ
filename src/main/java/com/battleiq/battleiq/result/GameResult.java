package com.battleiq.battleiq.result;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "room_code", nullable = false, length = 6)
    private String roomCode;

    @Column(name = "player_name", nullable = false, length = 20)
    private String playerName;

    @Column(name = "rank")
    private int rank;

    @Column(name = "total_score")
    private int totalScore;

    @Column(name = "correct_answers")
    private int correctAnswers;

    @Column(name = "total_questions")
    private int totalQuestions;

    @Column(name = "avg_response_time_ms")
    private double avgResponseTimeMs;

    @Column(name = "is_winner")
    private boolean isWinner;

    @Column(name = "rounds_played")
    private int roundsPlayed;

    @Column(name = "topic_data", columnDefinition = "TEXT")
    private String topicData;

    @Column(name = "roast", length = 255)
    private String roast;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
