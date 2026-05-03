package com.battleiq.battleiq.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomRepository extends JpaRepository<Room, String> {
    // JpaRepository gives us: save, findById, existsById, delete etc.
    // String = type of Primary Key (roomCode)
}