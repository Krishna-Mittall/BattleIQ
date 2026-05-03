package com.battleiq.battleiq.game;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GameRepository extends JpaRepository<GameSession, String> {

    // Get latest active session for a room
    Optional<GameSession> findTopByRoomCodeOrderByStartedAtDesc(String roomCode);
}