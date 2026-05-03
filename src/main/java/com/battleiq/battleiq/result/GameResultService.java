package com.battleiq.battleiq.result;

import com.battleiq.battleiq.game.GameSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameResultService {

    private final GameResultRepository resultRepository;

    // Called from GameService.endGame() — saves every player's result to MySQL
    public void saveResults(String roomCode, GameSession.FinalResultDTO finalResult) {
        try {
            for (GameSession.PlayerFinalScore score : finalResult.getLeaderboard()) {
                String roast = finalResult.getRoasts() != null
                        ? finalResult.getRoasts().getOrDefault(score.getPlayerName(), "")
                        : "";

                GameResult result = GameResult.builder()
                        .roomCode(roomCode)
                        .playerName(score.getPlayerName())
                        .rank(score.getRank())
                        .totalScore(score.getTotalScore())
                        .correctAnswers(score.getCorrectAnswers())
                        .totalQuestions(score.getTotalQuestions())
                        .avgResponseTimeMs(score.getAvgResponseTimeMs())
                        .isWinner(score.isWinner())
                        .roundsPlayed(finalResult.getRoundsPlayed())
                        .topicData(finalResult.getTopicData())
                        .roast(roast)
                        .createdAt(LocalDateTime.now())
                        .build();

                resultRepository.save(result);
            }
            log.info("Saved {} player results for room {}", finalResult.getLeaderboard().size(), roomCode);

        } catch (Exception e) {
            log.error("Failed to save results for room {}: {}", roomCode, e.getMessage());
        }
    }

    // Fetch results for a room — used if player refreshes result page
    public List<GameResult> getResultsByRoom(String roomCode) {
        return resultRepository.findByRoomCodeOrderByRankAsc(roomCode);
    }
}
