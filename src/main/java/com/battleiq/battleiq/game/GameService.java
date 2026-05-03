package com.battleiq.battleiq.game;

import com.battleiq.battleiq.ai.AIService;
import com.battleiq.battleiq.redis.RedisService;
import com.battleiq.battleiq.room.RoomService;
import com.battleiq.battleiq.websocket.GameWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final GameRepository gameRepository;
    private final RedisService redisService;
    private final AIService aiService;
    private final RoomService roomService;
    private final GameWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    private final com.battleiq.battleiq.result.GameResultService gameResultService;

    @Value("${game.timer.question}")
    private int questionTimerSeconds;

    @Value("${game.points.correct}")
    private int pointsCorrect;

    @Value("${game.points.speed.bonus}")
    private int pointsSpeedBonus;

    @Value("${game.points.speed.threshold}")
    private int speedThresholdSeconds;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final Set<String> processedQuestions = ConcurrentHashMap.newKeySet();
    private final Map<String, ScheduledFuture<?>> questionTimeouts = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> tieResponseTimeouts = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> postQuestionTransitions = new ConcurrentHashMap<>();

    @Async
    public void startGame(String roomCode) {
        try {
            List<String> players = redisService.getPlayersInRoom(roomCode);
            String topicData = redisService.getRoomTopicData(roomCode);
            List<Map<String, Object>> questions = prepareQuestionsForRound(
                    1,
                    aiService.generateQuestions(topicData, getAIDifficulty(GameSession.Difficulty.MEDIUM), 10)
            );

            GameSession session = GameSession.builder()
                    .roomCode(roomCode)
                    .roundNumber(1)
                    .difficulty(GameSession.Difficulty.MEDIUM)
                    .status(GameSession.GameStatus.QUESTION_IN_PROGRESS)
                    .questions(objectMapper.writeValueAsString(questions))
                    .startedAt(LocalDateTime.now())
                    .build();
            gameRepository.save(session);

            redisService.initScores(roomCode, players);
            redisService.storeQuestions(roomCode, questions);
            redisService.setCurrentQuestionIndex(roomCode, 0);
            redisService.setCurrentRound(roomCode, 1);
            redisService.setActivePlayers(roomCode, players);

            cancelTieResponseTimeout(roomCode);
            cancelPostQuestionTransition(roomCode);
            cancelQuestionTimeout(roomCode);

            broadcastNextQuestion(roomCode, 0, 1, GameSession.Difficulty.MEDIUM);
        } catch (Exception e) {
            log.error("Error starting game for room {}: {}", roomCode, e.getMessage());
        }
    }

    public void submitAnswer(GameSession.AnswerSubmitDTO dto) {
        String roomCode = dto.getRoomCode();
        String questionId = dto.getQuestionId();
        String playerName = dto.getPlayerName();
        String processKey = roomCode + ":" + questionId;
        List<String> activePlayers = redisService.getActivePlayers(roomCode);

        if (!activePlayers.contains(playerName)) {
            log.info("Ignoring answer from inactive player {} for room {}", playerName, roomCode);
            return;
        }

        if (processedQuestions.contains(processKey) ||
                redisService.hasPlayerAnswered(roomCode, questionId, playerName)) {
            log.info("Duplicate answer ignored from {} for question {}", playerName, questionId);
            return;
        }

        redisService.savePlayerAnswer(
                roomCode,
                questionId,
                playerName,
                dto.getSelectedOption(),
                dto.getTimeTakenMs()
        );

        int answered = redisService.getAnsweredCount(roomCode, questionId);
        int total = activePlayers.size();
        webSocketHandler.broadcastAnswerUpdate(roomCode, answered, total);

        if (answered >= total) {
            cancelQuestionTimeout(roomCode);
            scheduler.schedule(
                    () -> processQuestionEnd(roomCode, questionId),
                    500,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    public void processQuestionEnd(String roomCode, String questionId) {
        String processKey = roomCode + ":" + questionId;
        if (!processedQuestions.add(processKey)) {
            log.info("Question {} already processed for room {}, skipping", questionId, roomCode);
            return;
        }

        int currentIndex = redisService.getCurrentQuestionIndex(roomCode);
        Map<String, Object> currentQuestion = redisService.getQuestionByIndex(roomCode, currentIndex);
        if (currentQuestion == null || !questionId.equals(currentQuestion.get("id"))) {
            processedQuestions.remove(processKey);
            log.info("Ignoring stale question-end callback for room {} and question {}", roomCode, questionId);
            return;
        }

        cancelQuestionTimeout(roomCode);

        List<String> players = redisService.getActivePlayers(roomCode);
        Map<String, Object> question = redisService.getQuestion(roomCode, questionId);
        if (question == null) {
            processedQuestions.remove(processKey);
            return;
        }

        String correctAnswer = (String) question.get("correctAnswer");
        List<GameSession.PlayerAnswerResult> results = new ArrayList<>();

        for (String player : players) {
            Map<String, Object> answer = redisService.getPlayerAnswer(roomCode, questionId, player);

            boolean isTimeout = answer == null;
            String selected = isTimeout ? null : (String) answer.get("option");
            long timeTaken = isTimeout ? 0L : Long.parseLong(answer.get("timeTaken").toString());
            boolean isCorrect = !isTimeout && correctAnswer.equals(selected);
            int points = 0;

            if (isCorrect) {
                points += pointsCorrect;
                if (timeTaken <= speedThresholdSeconds * 1000L) {
                    points += pointsSpeedBonus;
                }
                redisService.incrementCorrectCount(roomCode, player);
            }

            if (points > 0) {
                redisService.addScore(roomCode, player, points);
            }
            if (!isTimeout) {
                redisService.recordResponseTime(roomCode, player, timeTaken);
            }

            results.add(GameSession.PlayerAnswerResult.builder()
                    .playerName(player)
                    .selectedOption(selected)
                    .isCorrect(isCorrect)
                    .isTimeout(isTimeout)
                    .pointsEarned(points)
                    .timeTakenMs(timeTaken)
                    .build());
        }

        Map<String, Integer> currentScores = redisService.getAllScores(roomCode);
        GameSession.QuestionResultDTO resultDTO = GameSession.QuestionResultDTO.builder()
                .questionId(questionId)
                .correctAnswer(correctAnswer)
                .playerResults(results)
                .currentScores(currentScores)
                .build();

        webSocketHandler.broadcastQuestionResult(roomCode, resultDTO);

        int totalQuestions = redisService.getTotalQuestions(roomCode);
        int roundNumber = redisService.getCurrentRound(roomCode);
        GameSession.Difficulty difficulty = roundNumber == 1
                ? GameSession.Difficulty.MEDIUM
                : roundNumber == 2
                ? GameSession.Difficulty.HARD
                : GameSession.Difficulty.VERY_HARD;

        cancelPostQuestionTransition(roomCode);
        ScheduledFuture<?> transitionFuture = scheduler.schedule(() -> {
            if (currentIndex + 1 < totalQuestions) {
                redisService.setCurrentQuestionIndex(roomCode, currentIndex + 1);
                broadcastNextQuestion(roomCode, currentIndex + 1, roundNumber, difficulty);
            } else {
                processRoundEnd(roomCode, roundNumber);
            }
        }, 3, TimeUnit.SECONDS);
        postQuestionTransitions.put(roomCode, transitionFuture);
    }

    private void processRoundEnd(String roomCode, int roundNumber) {
        Map<String, Integer> scores = redisService.getAllScores(roomCode);
        List<String> tiedPlayers = getFirstPlaceTiedPlayers(scores);

        if (tiedPlayers.isEmpty() || roundNumber >= 3) {
            endGame(roomCode, scores);
        } else {
            webSocketHandler.broadcastTieDetected(roomCode, tiedPlayers, scores);
            startTieResponseTimer(roomCode, tiedPlayers, roundNumber);
        }
    }

    private void startTieResponseTimer(String roomCode, List<String> tiedPlayers, int currentRound) {
        redisService.initTieResponses(roomCode, tiedPlayers);
        cancelTieResponseTimeout(roomCode);

        ScheduledFuture<?> tieFuture = scheduler.schedule(() -> {
            Map<String, Boolean> responses = redisService.getTieResponses(roomCode);
            boolean allAgreed = tiedPlayers.stream()
                    .allMatch(player -> Boolean.TRUE.equals(responses.get(player)));

            if (allAgreed) {
                startNextRound(roomCode, tiedPlayers, currentRound + 1);
            } else {
                endGame(roomCode, redisService.getAllScores(roomCode));
            }
        }, 15, TimeUnit.SECONDS);
        tieResponseTimeouts.put(roomCode, tieFuture);
    }

    public void handleTieResponse(GameSession.TieResponseDTO dto) {
        List<String> tiedPlayers = redisService.getTiedPlayers(dto.getRoomCode());
        if (!tiedPlayers.contains(dto.getPlayerName())) {
            log.info("Ignoring tie response from non-tied player {} in room {}", dto.getPlayerName(), dto.getRoomCode());
            return;
        }

        redisService.saveTieResponse(dto.getRoomCode(), dto.getPlayerName(), dto.isWantsRematch());

        Map<String, Boolean> responses = redisService.getTieResponses(dto.getRoomCode());
        boolean allResponded = tiedPlayers.stream().allMatch(responses::containsKey);

        if (allResponded) {
            cancelTieResponseTimeout(dto.getRoomCode());
            boolean allAgreed = tiedPlayers.stream()
                    .allMatch(player -> Boolean.TRUE.equals(responses.get(player)));
            int currentRound = redisService.getCurrentRound(dto.getRoomCode());

            if (allAgreed) {
                startNextRound(dto.getRoomCode(), tiedPlayers, currentRound + 1);
            } else {
                endGame(dto.getRoomCode(), redisService.getAllScores(dto.getRoomCode()));
            }
        }
    }

    private void startNextRound(String roomCode, List<String> tiedPlayers, int newRoundNumber) {
        GameSession.Difficulty difficulty = newRoundNumber == 2
                ? GameSession.Difficulty.HARD
                : GameSession.Difficulty.VERY_HARD;
        int questionCount = newRoundNumber == 2 ? 5 : 3;

        try {
            String topicData = redisService.getRoomTopicData(roomCode);
            List<Map<String, Object>> questions = prepareQuestionsForRound(
                    newRoundNumber,
                    aiService.generateQuestions(topicData, getAIDifficulty(difficulty), questionCount)
            );

            redisService.setCurrentRound(roomCode, newRoundNumber);
            redisService.setCurrentQuestionIndex(roomCode, 0);
            redisService.storeQuestions(roomCode, questions);
            redisService.setActivePlayers(roomCode, tiedPlayers);

            cancelPostQuestionTransition(roomCode);
            cancelQuestionTimeout(roomCode);
            cancelTieResponseTimeout(roomCode);

            processedQuestions.removeIf(key -> key.startsWith(roomCode + ":"));

            webSocketHandler.broadcastRoundStart(roomCode, newRoundNumber, tiedPlayers, getDifficultyLabel(difficulty));
            broadcastNextQuestion(roomCode, 0, newRoundNumber, difficulty);
        } catch (Exception e) {
            log.error("Error starting round {} for room {}: {}", newRoundNumber, roomCode, e.getMessage());
        }
    }

    private void endGame(String roomCode, Map<String, Integer> scores) {
        Map<String, Double> avgTimes = redisService.getAvgResponseTimes(roomCode);
        String topicData = redisService.getRoomTopicData(roomCode);

        List<Map.Entry<String, Integer>> sorted = scores.entrySet().stream()
                .sorted((left, right) -> {
                    int diff = right.getValue() - left.getValue();
                    if (diff != 0) {
                        return diff;
                    }
                    double leftTime = avgTimes.getOrDefault(left.getKey(), Double.MAX_VALUE);
                    double rightTime = avgTimes.getOrDefault(right.getKey(), Double.MAX_VALUE);
                    return Double.compare(leftTime, rightTime);
                })
                .collect(Collectors.toList());

        List<GameSession.PlayerFinalScore> leaderboard = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<String, Integer> entry : sorted) {
            String player = entry.getKey();
            int totalQ = redisService.getTotalQuestions(roomCode);
            int correct = redisService.getCorrectCount(roomCode, player);

            leaderboard.add(GameSession.PlayerFinalScore.builder()
                    .rank(rank)
                    .playerName(player)
                    .totalScore(entry.getValue())
                    .correctAnswers(correct)
                    .totalQuestions(totalQ)
                    .avgResponseTimeMs(avgTimes.getOrDefault(player, 0.0))
                    .isWinner(rank == 1)
                    .build());
            rank++;
        }

        String winnerName = leaderboard.isEmpty() ? null : leaderboard.get(0).getPlayerName();
        Map<String, String> roasts = aiService.generateRoasts(leaderboard);

        GameSession.FinalResultDTO finalResult = GameSession.FinalResultDTO.builder()
                .leaderboard(leaderboard)
                .winnerName(winnerName)
                .isTie(false)
                .topicData(topicData)
                .roasts(roasts)
                .roundsPlayed(redisService.getCurrentRound(roomCode))
                .build();

        webSocketHandler.broadcastGameEnd(roomCode, finalResult);
        gameResultService.saveResults(roomCode, finalResult);
        roomService.endRoom(roomCode);

        cancelQuestionTimeout(roomCode);
        cancelTieResponseTimeout(roomCode);
        cancelPostQuestionTransition(roomCode);

        processedQuestions.removeIf(key -> key.startsWith(roomCode + ":"));
        redisService.clearRoomData(roomCode);

        log.info("Game ended for room {}. Winner: {}", roomCode, winnerName);
    }

    private void broadcastNextQuestion(String roomCode, int index, int roundNumber, GameSession.Difficulty difficulty) {
        Map<String, Object> question = redisService.getQuestionByIndex(roomCode, index);
        if (question == null) {
            return;
        }

        int totalQuestions = redisService.getTotalQuestions(roomCode);
        int timer = difficulty == GameSession.Difficulty.VERY_HARD ? 10 : questionTimerSeconds;

        GameSession.QuestionDTO questionDTO = GameSession.QuestionDTO.builder()
                .questionId((String) question.get("id"))
                .questionNumber(index + 1)
                .totalQuestions(totalQuestions)
                .question((String) question.get("question"))
                .options((List<String>) question.get("options"))
                .timerSeconds(timer)
                .roundNumber(roundNumber)
                .difficulty(getDifficultyLabel(difficulty))
                .build();

        webSocketHandler.broadcastQuestion(roomCode, questionDTO);

        cancelQuestionTimeout(roomCode);
        String questionId = (String) question.get("id");
        ScheduledFuture<?> timeoutFuture = scheduler.schedule(
                () -> processQuestionEnd(roomCode, questionId),
                timer,
                TimeUnit.SECONDS
        );
        questionTimeouts.put(roomCode, timeoutFuture);
    }

    public GameSession getLatestSession(String roomCode) {
        return gameRepository.findTopByRoomCodeOrderByStartedAtDesc(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("No session found for room: " + roomCode));
    }

    private List<String> getFirstPlaceTiedPlayers(Map<String, Integer> scores) {
        if (scores.isEmpty()) {
            return Collections.emptyList();
        }
        int maxScore = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<String> topPlayers = scores.entrySet().stream()
                .filter(entry -> entry.getValue() == maxScore)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        return topPlayers.size() > 1 ? topPlayers : Collections.emptyList();
    }

    private String getDifficultyLabel(GameSession.Difficulty difficulty) {
        return switch (difficulty) {
            case MEDIUM -> "Easy";
            case HARD -> "Medium";
            case VERY_HARD -> "Hard";
        };
    }

    private String getAIDifficulty(GameSession.Difficulty difficulty) {
        return switch (difficulty) {
            case MEDIUM -> "EASY";
            case HARD -> "MEDIUM";
            case VERY_HARD -> "HARD";
        };
    }

    private List<Map<String, Object>> prepareQuestionsForRound(int roundNumber, List<Map<String, Object>> questions) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> question = new LinkedHashMap<>(questions.get(i));
            question.put("id", "r" + roundNumber + "_q" + (i + 1));
            normalized.add(question);
        }
        return normalized;
    }

    private void cancelQuestionTimeout(String roomCode) {
        ScheduledFuture<?> future = questionTimeouts.remove(roomCode);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void cancelTieResponseTimeout(String roomCode) {
        ScheduledFuture<?> future = tieResponseTimeouts.remove(roomCode);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void cancelPostQuestionTransition(String roomCode) {
        ScheduledFuture<?> future = postQuestionTransitions.remove(roomCode);
        if (future != null) {
            future.cancel(false);
        }
    }
}
