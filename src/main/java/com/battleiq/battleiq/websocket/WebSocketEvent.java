package com.battleiq.battleiq.websocket;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebSocketEvent {

    // Type tells frontend which handler to call
    // Possible values:
    // PLAYER_JOINED       → New player in lobby
    // NEXT_QUESTION       → New question for everyone
    // ANSWER_UPDATE       → X/N players answered
    // QUESTION_RESULT     → Correct answer + per-player results
    // TIE_DETECTED        → 1st place tie — show Yes/Skip screen
    // TIE_RESPONSE_UPDATE → Live update of who responded
    // ROUND_START         → New round starting (Round 2 / Final)
    // PLAYER_LEFT         → Someone disconnected
    // GAME_END            → Final leaderboard + roasts
    private String type;

    // Actual payload — differs per event type
    // Frontend reads this based on "type"
    private Object data;
}