package com.battleiq.battleiq.room;

import com.battleiq.battleiq.redis.RedisService;
import com.battleiq.battleiq.websocket.GameWebSocketHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RedisService redisService;
    private final GameWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    // ─── Create Room ────────────────────────────────────────────────────────

    public Room.RoomResponse createRoom(Room.CreateRoomRequest request) {

        if (request.getMaxPlayers() < 2 || request.getMaxPlayers() > 10) {
            throw new IllegalArgumentException("Players must be between 2 and 10");
        }
        if (request.getTopicType() == null || request.getTopicData() == null) {
            throw new IllegalArgumentException("Topic is required");
        }

        String roomCode = generateUniqueRoomCode();

        String normalizedTopicData = normalizeTopicData(request.getTopicType(), request.getTopicData());

        Room room = Room.builder()
                .roomCode(roomCode)
                .hostName(request.getHostName().trim())
                .topicType(Room.TopicType.valueOf(request.getTopicType().toUpperCase()))
                .topicData(normalizedTopicData)
                .maxPlayers(request.getMaxPlayers())
                .status(Room.RoomStatus.WAITING)
                .createdAt(LocalDateTime.now())
                .build();

        roomRepository.save(room);

        // Add host to Redis
        redisService.addPlayerToRoom(roomCode, request.getHostName().trim());

        // ✅ FIX 1 — Store topic in Redis so GameService can access during question generation
        redisService.setRoomTopicData(roomCode, normalizedTopicData);

        // Init round to 1
        redisService.setCurrentRound(roomCode, 1);

        return buildRoomResponse(room, redisService.getPlayersInRoom(roomCode));
    }

    // ─── Join Room ──────────────────────────────────────────────────────────

    public Room.JoinRoomResponse joinRoom(Room.JoinRoomRequest request) {

        String roomCode = request.getRoomCode().trim().toUpperCase();
        String playerName = request.getPlayerName().trim();

        Room room = roomRepository.findById(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomCode));

        if (room.getStatus() != Room.RoomStatus.WAITING) {
            return new Room.JoinRoomResponse(roomCode, null, false, "Game already started");
        }

        List<String> currentPlayers = redisService.getPlayersInRoom(roomCode);
        if (currentPlayers.size() >= room.getMaxPlayers()) {
            return new Room.JoinRoomResponse(roomCode, null, false, "Room is full");
        }

        String assignedName = resolvePlayerName(playerName, currentPlayers);
        redisService.addPlayerToRoom(roomCode, assignedName);

        // ✅ FIX 2 — Broadcast to lobby so all players see new joiner in real-time
        List<String> updatedPlayers = redisService.getPlayersInRoom(roomCode);
        webSocketHandler.broadcastPlayerJoined(roomCode, assignedName, updatedPlayers);

        return new Room.JoinRoomResponse(roomCode, assignedName, true, "Joined successfully");
    }

    // ─── Get Room Details ───────────────────────────────────────────────────

    public Room.RoomResponse getRoomDetails(String roomCode) {
        Room room = roomRepository.findById(roomCode.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomCode));
        List<String> players = redisService.getPlayersInRoom(roomCode.toUpperCase());
        return buildRoomResponse(room, players);
    }

    // ─── Mark Room Active (called before GameService.startGame) ─────────────

    public void markRoomActive(String roomCode, String hostName) {
        Room room = roomRepository.findById(roomCode.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        if (!room.getHostName().equals(hostName)) {
            throw new IllegalArgumentException("Only host can start the game");
        }

        List<String> players = redisService.getPlayersInRoom(roomCode.toUpperCase());
        if (players.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 players to start");
        }

        room.setStatus(Room.RoomStatus.ACTIVE);
        roomRepository.save(room);
        redisService.setRoomStatus(roomCode.toUpperCase(), "ACTIVE");
    }

    // ─── End Room ───────────────────────────────────────────────────────────

    public void endRoom(String roomCode) {
        roomRepository.findById(roomCode.toUpperCase()).ifPresent(room -> {
            room.setStatus(Room.RoomStatus.ENDED);
            roomRepository.save(room);
        });
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private String generateUniqueRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
            code = sb.toString();
        } while (roomRepository.existsById(code));
        return code;
    }

    private String resolvePlayerName(String name, List<String> existingPlayers) {
        if (name.length() > 20) name = name.substring(0, 20);
        if (!existingPlayers.contains(name)) return name;
        int suffix = 2;
        String candidate;
        do { candidate = name + "_" + suffix++; } while (existingPlayers.contains(candidate));
        return candidate;
    }

    private Room.RoomResponse buildRoomResponse(Room room, List<String> players) {
        return Room.RoomResponse.builder()
                .roomCode(room.getRoomCode())
                .hostName(room.getHostName())
                .topicType(room.getTopicType().name())
                .topicData(room.getTopicData())
                .maxPlayers(room.getMaxPlayers())
                .status(room.getStatus().name())
                .players(players)
                .build();
    }

    private String normalizeTopicData(String topicType, String topicData) {
        try {
            JsonNode node = objectMapper.readTree(topicData);
            if (node.isObject() && node.get("type") == null) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) node)
                        .put("type", topicType.toUpperCase());
                return objectMapper.writeValueAsString(node);
            }
        } catch (Exception ignored) {
        }
        return topicData;
    }
}
