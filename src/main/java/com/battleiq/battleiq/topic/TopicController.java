package com.battleiq.battleiq.topic;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/topic")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class TopicController {

    private final TopicService topicService;

    // ─── GET /api/topic/search/movies?query=avengers ──────────────────────────
    // Called while host types in Movie search box (debounced from frontend)
    @GetMapping("/search/movies")
    public ResponseEntity<List<TopicDTO.MovieResult>> searchMovies(
            @RequestParam String query) {

        if (query.trim().length() < 2) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(topicService.searchMovies(query));
    }

    // ─── GET /api/topic/search/series?query=game+of+thrones ──────────────────
    // Called while host types in Series search box
    @GetMapping("/search/series")
    public ResponseEntity<List<TopicDTO.SeriesResult>> searchSeries(
            @RequestParam String query) {

        if (query.trim().length() < 2) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(topicService.searchSeries(query));
    }

    // ─── GET /api/topic/series/{id}/seasons ───────────────────────────────────
    // Returns all seasons with episode counts for selected series
    // Used to populate season dropdown in UI
    @GetMapping("/series/{id}/seasons")
    public ResponseEntity<List<TopicDTO.SeasonInfo>> getSeasons(
            @PathVariable int id) {

        List<TopicDTO.SeasonInfo> seasons = topicService.getSeriesSeasons(id);
        if (seasons.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(seasons);
    }

    // ─── GET /api/topic/series/{id}/season/{number}/episodes ─────────────────
    // Returns episode count for a specific season
    // Used to limit episode dropdown based on selected season
    @GetMapping("/series/{id}/season/{number}/episodes")
    public ResponseEntity<?> getEpisodeCount(
            @PathVariable int id,
            @PathVariable int number) {

        int count = topicService.getEpisodeCount(id, number);
        if (count == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(java.util.Map.of("episodeCount", count));
    }

    // ─── POST /api/topic/validate/custom ─────────────────────────────────────
    // AI validates custom topic entered by host
    // Returns VALID / AMBIGUOUS / TOO_BROAD / INVALID with suggestions
    @PostMapping("/validate/custom")
    public ResponseEntity<TopicDTO.CustomTopicValidationResult> validateCustomTopic(
            @RequestBody TopicDTO.CustomTopicRequest request) {

        TopicDTO.CustomTopicValidationResult result =
                topicService.validateCustomTopic(request.getInput());
        return ResponseEntity.ok(result);
    }
}