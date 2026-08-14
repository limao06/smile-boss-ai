package com.smileboss.activity;

import com.smileboss.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/activity")
public class ActivityController {
    private final ActivityService service;
    public ActivityController(ActivityService service) { this.service = service; }

    @PostMapping("/events")
    public ApiResponse<Void> record(@RequestBody EventRequest body) { service.record(body.candidateId(), body.eventType(), body.objectType(), body.objectId(), body.metadata()); return ApiResponse.ok(); }

    @GetMapping("/candidates/{candidateId}/analysis")
    public ApiResponse<Map<String, Object>> analyze(@PathVariable long candidateId) { return ApiResponse.ok(service.analyze(candidateId)); }

    public record EventRequest(long candidateId, @NotBlank String eventType, String objectType, Long objectId, Map<String, Object> metadata) {}
}

