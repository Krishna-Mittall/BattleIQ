package com.battleiq.battleiq.result;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/result")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ResultController {

    private final GameResultService gameResultService;

    // ─── GET /api/result/{roomCode} ───────────────────────────────────────────
    // Called when player refreshes result page — fetches saved result from MySQL
    @GetMapping("/{roomCode}")
    public ResponseEntity<?> getResult(@PathVariable String roomCode) {
        try {
            List<GameResult> results = gameResultService.getResultsByRoom(roomCode.toUpperCase());
            if (results.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}