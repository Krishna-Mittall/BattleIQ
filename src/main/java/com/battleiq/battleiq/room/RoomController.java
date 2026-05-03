package com.battleiq.battleiq.room;

import com.battleiq.battleiq.game.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/room")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@Slf4j
public class RoomController {

    private final RoomService roomService;
    private final GameService gameService;

    @PostMapping("/create")
    public ResponseEntity<?> createRoom(@RequestBody Room.CreateRoomRequest request) {
        try {
            return ResponseEntity.ok(roomService.createRoom(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Create room failed", e);
            return ResponseEntity.internalServerError().body("Create room failed: " + e.getMessage());
        }
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinRoom(@RequestBody Room.JoinRoomRequest request) {
        try {
            Room.JoinRoomResponse response = roomService.joinRoom(request);
            if (!response.isSuccess()) {
                return ResponseEntity.badRequest().body(response);
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Join room failed", e);
            return ResponseEntity.internalServerError().body("Join room failed: " + e.getMessage());
        }
    }

    @GetMapping("/{code}")
    public ResponseEntity<?> getRoomDetails(@PathVariable String code) {
        try {
            return ResponseEntity.ok(roomService.getRoomDetails(code));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Get room details failed for {}", code, e);
            return ResponseEntity.internalServerError().body("Get room failed: " + e.getMessage());
        }
    }

    @PostMapping("/{code}/start")
    public ResponseEntity<?> startGame(
            @PathVariable String code,
            @RequestParam String hostName) {
        try {
            roomService.markRoomActive(code, hostName);
            gameService.startGame(code);
            return ResponseEntity.ok("Game starting...");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Start game failed for room {}", code, e);
            return ResponseEntity.internalServerError().body("Start game failed: " + e.getMessage());
        }
    }
}
