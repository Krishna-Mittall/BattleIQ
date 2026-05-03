package com.battleiq.battleiq.room;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @Column(name = "room_code", length = 6)
    private String roomCode;

    @Column(name = "host_name", nullable = false, length = 20)
    private String hostName;

    @Enumerated(EnumType.STRING)
    @Column(name = "topic_type", nullable = false)
    private TopicType topicType;

    // Stores topic details as JSON string
    // Movie  → {"id":123, "title":"Avengers", "year":2019}
    // Series → {"id":456, "title":"GOT", "seasonFrom":1, "seasonTo":3, "episodeUpto":5}
    // Custom → {"rawInput":"Java Multithreading", "normalized":"Java Multithreading"}
    @Column(name = "topic_data", columnDefinition = "TEXT")
    private String topicData;

    @Column(name = "max_players")
    private int maxPlayers;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RoomStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ─── Enums ──────────────────────────────────────────────────────────────

    public enum TopicType {
        MOVIE, SERIES, CUSTOM
    }

    public enum RoomStatus {
        WAITING,  // Players joining, game not started
        ACTIVE,   // Game in progress
        ENDED     // Game finished
    }

    // ─── Request DTOs ───────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRoomRequest {
        private String hostName;         // Player name from localStorage
        private String topicType;        // "MOVIE" | "SERIES" | "CUSTOM"
        private String topicData;        // JSON string of topic details
        private int maxPlayers;          // 2 to 10
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinRoomRequest {
        private String roomCode;
        private String playerName;
    }

    // ─── Response DTOs ──────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoomResponse {
        private String roomCode;
        private String hostName;
        private String topicType;
        private String topicData;
        private int maxPlayers;
        private String status;
        private java.util.List<String> players; // Live from Redis
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinRoomResponse {
        private String roomCode;
        private String assignedName;  // May differ if duplicate (Rahul → Rahul_2)
        private boolean success;
        private String message;
    }
}