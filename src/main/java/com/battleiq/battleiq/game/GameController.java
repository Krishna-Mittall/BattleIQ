package com.battleiq.battleiq.game;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class GameController {

    private final GameService gameService;

    // ─── POST /api/game/start ────────────────────────────────────────────────
    // Called after host clicks "Start Game" in lobby
    // Triggers AI question generation + broadcasts first question
    @PostMapping("/start")
    public ResponseEntity<?> startGame(@RequestParam String roomCode) {
        try {
            gameService.startGame(roomCode);
            return ResponseEntity.ok("Game starting...");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ─── POST /api/game/answer ───────────────────────────────────────────────
    // Player submits answer (also sent via WebSocket, REST as fallback)
    @PostMapping("/answer")
    public ResponseEntity<?> submitAnswer(@RequestBody GameSession.AnswerSubmitDTO dto) {
        try {
            gameService.submitAnswer(dto);
            return ResponseEntity.ok("Answer received");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ─── POST /api/game/tie-response ─────────────────────────────────────────
    // Tied player responds to "Wanna play harder round?" (Yes / Skip)
    @PostMapping("/tie-response")
    public ResponseEntity<?> tieResponse(@RequestBody GameSession.TieResponseDTO dto) {
        try {
            gameService.handleTieResponse(dto);
            return ResponseEntity.ok("Response recorded");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ─── GET /api/game/{roomCode}/result ─────────────────────────────────────
    // Fetch final result (for page refresh recovery)
    @GetMapping("/{roomCode}/result")
    public ResponseEntity<?> getResult(@PathVariable String roomCode) {
        try {
            GameSession session = gameService.getLatestSession(roomCode);
            return ResponseEntity.ok(session);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}