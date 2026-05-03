package com.battleiq.battleiq.websocket;

import com.battleiq.battleiq.game.GameService;
import com.battleiq.battleiq.game.GameSession;
import com.battleiq.battleiq.redis.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

@Controller
@Slf4j
public class GameWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final GameService gameService;

    @Autowired
    public GameWebSocketHandler(SimpMessagingTemplate messagingTemplate,
                                RedisService redisService,
                                ObjectMapper objectMapper,
                                @Lazy GameService gameService) {
        this.messagingTemplate = messagingTemplate;
        this.redisService = redisService;
        this.objectMapper = objectMapper;
        this.gameService = gameService;
    }

    // ─── INBOUND — Client → Server ───────────────────────────────────────────

    @MessageMapping("/answer")
    public void receiveAnswer(@Payload GameSession.AnswerSubmitDTO dto) {
        try {
            // GameService handles everything — save + count + process
            gameService.submitAnswer(dto);
        } catch (Exception e) {
            log.error("Error handling answer from {}: {}", dto.getPlayerName(), e.getMessage());
        }
    }

    @MessageMapping("/tie-response")
    public void receiveTieResponse(@Payload GameSession.TieResponseDTO dto) {
        try {
            // ✅ FIX — removed duplicate redisService.saveTieResponse() call here
            // GameService.handleTieResponse() does save + broadcast + logic — all in one place
            // Doing it here AND in GameService caused double-save and incorrect tie logic

            // Broadcast live status first (before delegating to GameService)
            // GameService will save the response, then check if all responded
            gameService.handleTieResponse(dto);

            // Broadcast updated responses to all players
            Map<String, Boolean> responses = redisService.getTieResponses(dto.getRoomCode());
            List<String> tiedPlayers = redisService.getTiedPlayers(dto.getRoomCode());

            broadcastToRoom(dto.getRoomCode(), WebSocketEvent.builder()
                    .type("TIE_RESPONSE_UPDATE")
                    .data(Map.of(
                            "responses", responses,
                            "tiedPlayers", tiedPlayers
                    ))
                    .build());

        } catch (Exception e) {
            log.error("Error handling tie response from {}: {}", dto.getPlayerName(), e.getMessage());
        }
    }

    @MessageMapping("/leave")
    public void receiveLeave(@Payload Map<String, String> payload) {
        String roomCode = payload.get("roomCode");
        String playerName = payload.get("playerName");
        if (roomCode == null || playerName == null) return;

        redisService.removePlayerFromRoom(roomCode, playerName);

        List<String> remaining = redisService.getPlayersInRoom(roomCode);
        broadcastToRoom(roomCode, WebSocketEvent.builder()
                .type("PLAYER_LEFT")
                .data(Map.of(
                        "playerName", playerName,
                        "remainingPlayers", remaining
                ))
                .build());

        log.info("Player {} left room {}", playerName, roomCode);
    }

    // ─── OUTBOUND — Server → All Clients ─────────────────────────────────────

    public void broadcastPlayerJoined(String roomCode, String playerName, List<String> allPlayers) {
        broadcastToRoom(roomCode, WebSocketEvent.builder()
                .type("PLAYER_JOINED")
                .data(Map.of(
                        "playerName", playerName,
                        "allPlayers", allPlayers
                ))
                .build());
    }

    public void broadcastQuestion(String roomCode, GameSession.QuestionDTO question) {
        broadcastToRoom(roomCode, WebSocketEvent.builder()
                .type("NEXT_QUESTION")
                .data(question)
                .build());
        log.info("Broadcasted Q{} to room {}", question.getQuestionNumber(), roomCode);
    }

    public void broadcastAnswerUpdate(String roomCode, int answered, int total) {
        broadcastToRoom(roomCode, WebSocketEvent.builder()
                .type("ANSWER_UPDATE")
                .data(Map.of(
                        "answered", answered,
                        "total", total
                ))
                .build());
    }

    public void broadcastQuestionResult(String roomCode, GameSession.QuestionResultDTO result) {
        broadcastToRoom(roomCode, WebSocketEvent.builder()
                .type("QUESTION_RESULT")
                .data(result)
                .build());
    }

    public void broadcastTieDetected(String roomCode, List<String> tiedPlayers, Map<String, Integer> scores) {
        broadcastToRoom(roomCode, WebSocketEvent.builder()
                .type("TIE_DETECTED")
                .data(Map.of(
                        "tiedPlayers", tiedPlayers,
                        "scores", scores,
                        "timerSeconds", 15,
                        "message", "Tie at 1st place! Wanna play a harder round?"
                ))
                .build());
    }

    public void broadcastRoundStart(String roomCode, int roundNumber,
                                    List<String> activePlayers, String difficulty) {
        broadcastToRoom(roomCode, WebSocketEvent.builder()
                .type("ROUND_START")
                .data(Map.of(
                        "roundNumber", roundNumber,
                        "activePlayers", activePlayers,
                        "difficulty", difficulty,
                        "message", "Round " + roundNumber + " — " + difficulty + " questions!"
                ))
                .build());
    }

    public void broadcastGameEnd(String roomCode, GameSession.FinalResultDTO result) {
        broadcastToRoom(roomCode, WebSocketEvent.builder()
                .type("GAME_END")
                .data(result)
                .build());
        log.info("Game ended broadcast sent to room {}", roomCode);
    }

    // ─── Private Helper ───────────────────────────────────────────────────────

    private void broadcastToRoom(String roomCode, WebSocketEvent event) {
        try {
            messagingTemplate.convertAndSend("/topic/room/" + roomCode, event);
        } catch (Exception e) {
            log.error("Failed to broadcast {} to room {}: {}", event.getType(), roomCode, e.getMessage());
        }
    }
}