package com.battleiq.battleiq.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final long ROOM_TTL_HOURS = 4;

    // ─── KEY CONSTANTS ───────────────────────────────────────────────────────

    private String keyPlayers(String roomCode)       { return "battleiq:" + roomCode + ":players"; }
    private String keyStatus(String roomCode)        { return "battleiq:" + roomCode + ":status"; }
    private String keyTopicData(String roomCode)     { return "battleiq:" + roomCode + ":topic"; }
    private String keyQuestions(String roomCode)     { return "battleiq:" + roomCode + ":questions"; }
    private String keyCurrentIndex(String roomCode)  { return "battleiq:" + roomCode + ":qindex"; }
    private String keyCurrentRound(String roomCode)  { return "battleiq:" + roomCode + ":round"; }
    private String keyActivePlayers(String roomCode) { return "battleiq:" + roomCode + ":active:players"; }
    private String keyScores(String roomCode)        { return "battleiq:" + roomCode + ":scores"; }
    private String keyCorrectCount(String roomCode)  { return "battleiq:" + roomCode + ":correct"; }
    private String keyResponseTimes(String roomCode) { return "battleiq:" + roomCode + ":times"; }
    private String keyAnswers(String roomCode, String questionId) {
        return "battleiq:" + roomCode + ":answers:" + questionId;
    }
    private String keyTieResponses(String roomCode)  { return "battleiq:" + roomCode + ":tie:responses"; }
    private String keyTiedPlayers(String roomCode)   { return "battleiq:" + roomCode + ":tie:players"; }

    // ─── ROOM — PLAYERS ──────────────────────────────────────────────────────

    public void addPlayerToRoom(String roomCode, String playerName) {
        redisTemplate.opsForList().rightPush(keyPlayers(roomCode), playerName);
        redisTemplate.expire(keyPlayers(roomCode), ROOM_TTL_HOURS, TimeUnit.HOURS);
    }

    public void removePlayerFromRoom(String roomCode, String playerName) {
        redisTemplate.opsForList().remove(keyPlayers(roomCode), 1, playerName);
    }

    public List<String> getPlayersInRoom(String roomCode) {
        List<Object> raw = redisTemplate.opsForList().range(keyPlayers(roomCode), 0, -1);
        if (raw == null) return new ArrayList<>();
        List<String> players = new ArrayList<>();
        for (Object o : raw) players.add(o.toString());
        return players;
    }

    // ─── ROOM — STATUS & TOPIC ───────────────────────────────────────────────

    public void setRoomStatus(String roomCode, String status) {
        redisTemplate.opsForValue().set(keyStatus(roomCode), status, ROOM_TTL_HOURS, TimeUnit.HOURS);
    }

    public String getRoomStatus(String roomCode) {
        Object val = redisTemplate.opsForValue().get(keyStatus(roomCode));
        return val != null ? val.toString() : null;
    }

    public void setRoomTopicData(String roomCode, String topicDataJson) {
        redisTemplate.opsForValue().set(keyTopicData(roomCode), topicDataJson, ROOM_TTL_HOURS, TimeUnit.HOURS);
    }

    public String getRoomTopicData(String roomCode) {
        Object val = redisTemplate.opsForValue().get(keyTopicData(roomCode));
        return val != null ? val.toString() : null;
    }

    // ─── GAME — QUESTIONS ────────────────────────────────────────────────────

    public void storeQuestions(String roomCode, List<Map<String, Object>> questions) {
        try {
            String json = objectMapper.writeValueAsString(questions);
            redisTemplate.opsForValue().set(keyQuestions(roomCode), json, ROOM_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Failed to store questions for room {}: {}", roomCode, e.getMessage());
        }
    }

    public Map<String, Object> getQuestionByIndex(String roomCode, int index) {
        try {
            Object val = redisTemplate.opsForValue().get(keyQuestions(roomCode));
            if (val == null) return null;
            List<Map<String, Object>> questions = objectMapper.readValue(
                    val.toString(), new TypeReference<>() {});
            return (index < questions.size()) ? questions.get(index) : null;
        } catch (Exception e) {
            log.error("Failed to get question by index {} for room {}: {}", index, roomCode, e.getMessage());
            return null;
        }
    }

    public Map<String, Object> getQuestion(String roomCode, String questionId) {
        try {
            Object val = redisTemplate.opsForValue().get(keyQuestions(roomCode));
            if (val == null) return null;
            List<Map<String, Object>> questions = objectMapper.readValue(
                    val.toString(), new TypeReference<>() {});
            return questions.stream()
                    .filter(q -> questionId.equals(q.get("id")))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            log.error("Failed to get question {} for room {}: {}", questionId, roomCode, e.getMessage());
            return null;
        }
    }

    public int getTotalQuestions(String roomCode) {
        try {
            Object val = redisTemplate.opsForValue().get(keyQuestions(roomCode));
            if (val == null) return 0;
            List<?> questions = objectMapper.readValue(val.toString(), List.class);
            return questions.size();
        } catch (Exception e) { return 0; }
    }

    // ─── GAME — QUESTION INDEX & ROUND ───────────────────────────────────────

    public void setCurrentQuestionIndex(String roomCode, int index) {
        redisTemplate.opsForValue().set(keyCurrentIndex(roomCode), String.valueOf(index), ROOM_TTL_HOURS, TimeUnit.HOURS);
    }

    public int getCurrentQuestionIndex(String roomCode) {
        Object val = redisTemplate.opsForValue().get(keyCurrentIndex(roomCode));
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }

    public void setCurrentRound(String roomCode, int round) {
        redisTemplate.opsForValue().set(keyCurrentRound(roomCode), String.valueOf(round), ROOM_TTL_HOURS, TimeUnit.HOURS);
    }

    public int getCurrentRound(String roomCode) {
        Object val = redisTemplate.opsForValue().get(keyCurrentRound(roomCode));
        return val != null ? Integer.parseInt(val.toString()) : 1;
    }

    public void setActivePlayers(String roomCode, List<String> players) {
        try {
            redisTemplate.opsForValue().set(
                    keyActivePlayers(roomCode),
                    objectMapper.writeValueAsString(players),
                    ROOM_TTL_HOURS,
                    TimeUnit.HOURS
            );
        } catch (Exception e) {
            log.error("Failed to store active players for room {}: {}", roomCode, e.getMessage());
        }
    }

    public List<String> getActivePlayers(String roomCode) {
        try {
            Object val = redisTemplate.opsForValue().get(keyActivePlayers(roomCode));
            if (val == null) {
                return getPlayersInRoom(roomCode);
            }
            return objectMapper.readValue(val.toString(), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to read active players for room {}: {}", roomCode, e.getMessage());
            return getPlayersInRoom(roomCode);
        }
    }

    // ─── GAME — SCORES ───────────────────────────────────────────────────────

    public void initScores(String roomCode, List<String> players) {
        for (String player : players) {
            redisTemplate.opsForHash().put(keyScores(roomCode), player, "0");
            redisTemplate.opsForHash().put(keyCorrectCount(roomCode), player, "0");
        }
        redisTemplate.expire(keyScores(roomCode), ROOM_TTL_HOURS, TimeUnit.HOURS);
        redisTemplate.expire(keyCorrectCount(roomCode), ROOM_TTL_HOURS, TimeUnit.HOURS);
    }

    public void addScore(String roomCode, String playerName, int points) {
        redisTemplate.opsForHash().increment(keyScores(roomCode), playerName, points);
    }

    public Map<String, Integer> getAllScores(String roomCode) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(keyScores(roomCode));
        Map<String, Integer> scores = new LinkedHashMap<>();
        raw.forEach((k, v) -> scores.put(k.toString(), Integer.parseInt(v.toString())));
        return scores;
    }

    // ─── GAME — CORRECT COUNT ────────────────────────────────────────────────

    public void incrementCorrectCount(String roomCode, String playerName) {
        redisTemplate.opsForHash().increment(keyCorrectCount(roomCode), playerName, 1);
    }

    public int getCorrectCount(String roomCode, String playerName) {
        Object val = redisTemplate.opsForHash().get(keyCorrectCount(roomCode), playerName);
        return val != null ? Integer.parseInt(val.toString()) : 0;
    }

    // ─── GAME — RESPONSE TIMES ───────────────────────────────────────────────

    public void recordResponseTime(String roomCode, String playerName, long timeTakenMs) {
        String sumKey = playerName + ":sum";
        String countKey = playerName + ":count";
        redisTemplate.opsForHash().increment(keyResponseTimes(roomCode), sumKey, timeTakenMs);
        redisTemplate.opsForHash().increment(keyResponseTimes(roomCode), countKey, 1);
        redisTemplate.expire(keyResponseTimes(roomCode), ROOM_TTL_HOURS, TimeUnit.HOURS);
    }

    public Map<String, Double> getAvgResponseTimes(String roomCode) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(keyResponseTimes(roomCode));
        Map<String, Double> avgTimes = new HashMap<>();
        for (Object key : raw.keySet()) {
            String k = key.toString();
            if (k.endsWith(":sum")) {
                String player = k.replace(":sum", "");
                long sum = Long.parseLong(raw.get(k).toString());
                Object countObj = raw.get(player + ":count");
                long count = countObj != null ? Long.parseLong(countObj.toString()) : 1;
                avgTimes.put(player, count > 0 ? (double) sum / count : Double.MAX_VALUE);
            }
        }
        return avgTimes;
    }

    // ─── GAME — PLAYER ANSWERS ───────────────────────────────────────────────

    public void savePlayerAnswer(String roomCode, String questionId,
                                 String playerName, String option, long timeTakenMs) {
        try {
            String answer = objectMapper.writeValueAsString(
                    Map.of("option", option, "timeTaken", String.valueOf(timeTakenMs))
            );
            redisTemplate.opsForHash().put(keyAnswers(roomCode, questionId), playerName, answer);
            redisTemplate.expire(keyAnswers(roomCode, questionId), ROOM_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Failed to save answer for player {} in room {}: {}", playerName, roomCode, e.getMessage());
        }
    }

    public Map<String, Object> getPlayerAnswer(String roomCode, String questionId, String playerName) {
        Object raw = redisTemplate.opsForHash().get(keyAnswers(roomCode, questionId), playerName);
        if (raw == null) return null;
        try {
            return objectMapper.readValue(raw.toString(), new TypeReference<>() {});
        } catch (Exception e) { return null; }
    }

    public int getAnsweredCount(String roomCode, String questionId) {
        Long count = redisTemplate.opsForHash().size(keyAnswers(roomCode, questionId));
        return count != null ? count.intValue() : 0;
    }

    // ✅ NEW — Check if player already answered this question (prevent duplicate submissions)
    public boolean hasPlayerAnswered(String roomCode, String questionId, String playerName) {
        return redisTemplate.opsForHash().hasKey(keyAnswers(roomCode, questionId), playerName);
    }

    // ─── TIE HANDLING ────────────────────────────────────────────────────────

    public void initTieResponses(String roomCode, List<String> tiedPlayers) {
        try {
            redisTemplate.opsForValue().set(
                    keyTiedPlayers(roomCode),
                    objectMapper.writeValueAsString(tiedPlayers),
                    15, TimeUnit.MINUTES
            );
            redisTemplate.delete(keyTieResponses(roomCode));
        } catch (Exception e) {
            log.error("Failed to init tie responses for room {}: {}", roomCode, e.getMessage());
        }
    }

    public void saveTieResponse(String roomCode, String playerName, boolean wantsRematch) {
        redisTemplate.opsForHash().put(keyTieResponses(roomCode), playerName, String.valueOf(wantsRematch));
        redisTemplate.expire(keyTieResponses(roomCode), 15, TimeUnit.MINUTES);
    }

    public Map<String, Boolean> getTieResponses(String roomCode) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(keyTieResponses(roomCode));
        Map<String, Boolean> responses = new HashMap<>();
        raw.forEach((k, v) -> responses.put(k.toString(), Boolean.parseBoolean(v.toString())));
        return responses;
    }

    public List<String> getTiedPlayers(String roomCode) {
        try {
            Object val = redisTemplate.opsForValue().get(keyTiedPlayers(roomCode));
            if (val == null) return new ArrayList<>();
            return objectMapper.readValue(val.toString(), new TypeReference<>() {});
        } catch (Exception e) { return new ArrayList<>(); }
    }

    // ─── CLEANUP ─────────────────────────────────────────────────────────────

    public void clearRoomData(String roomCode) {
        Set<String> keys = redisTemplate.keys("battleiq:" + roomCode + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Cleared Redis data for room {}", roomCode);
        }
    }
}
