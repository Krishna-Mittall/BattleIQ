package com.battleiq.battleiq.room;

import com.battleiq.battleiq.game.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/room")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class RoomController {

    private final RoomService roomService;
    private final GameService gameService;

    // ─── POST /api/room/create ───────────────────────────────────────────────
    @PostMapping("/create")
    public ResponseEntity<?> createRoom(@RequestBody Room.CreateRoomRequest request) {
        try {
            return ResponseEntity.ok(roomService.createRoom(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ─── POST /api/room/join ─────────────────────────────────────────────────
    @PostMapping("/join")
    public ResponseEntity<?> joinRoom(@RequestBody Room.JoinRoomRequest request) {
        try {
            Room.JoinRoomResponse response = roomService.joinRoom(request);
            if (!response.isSuccess()) return ResponseEntity.badRequest().body(response);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ─── GET /api/room/{code} ────────────────────────────────────────────────
    @GetMapping("/{code}")
    public ResponseEntity<?> getRoomDetails(@PathVariable String code) {
        try {
            return ResponseEntity.ok(roomService.getRoomDetails(code));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ─── POST /api/room/{code}/start ─────────────────────────────────────────
    // Step 1 — markRoomActive: validates host + updates MySQL + Redis status
    // Step 2 — gameService.startGame: generates questions + broadcasts first question
    @PostMapping("/{code}/start")
    public ResponseEntity<?> startGame(
            @PathVariable String code,
            @RequestParam String hostName) {
        try {
            roomService.markRoomActive(code, hostName);  // validate + status update
            gameService.startGame(code);                  // ✅ FIX — actual game trigger
            return ResponseEntity.ok("Game starting...");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}