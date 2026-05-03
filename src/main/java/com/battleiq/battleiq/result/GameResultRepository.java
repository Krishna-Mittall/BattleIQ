package com.battleiq.battleiq.result;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameResultRepository extends JpaRepository<GameResult, String> {

    // Get all results for a room — used to show leaderboard history
    List<GameResult> findByRoomCodeOrderByRankAsc(String roomCode);
}