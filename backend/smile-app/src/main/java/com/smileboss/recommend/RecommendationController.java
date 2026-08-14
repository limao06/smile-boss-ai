package com.smileboss.recommend;

import com.smileboss.ai.LlmGateway;
import com.smileboss.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class RecommendationController {
    private final RecommendationService recommendations;
    private final LlmGateway llm;
    public RecommendationController(RecommendationService recommendations, LlmGateway llm) { this.recommendations = recommendations; this.llm = llm; }

    @PostMapping("/candidates/{candidateId}/recommend-jobs")
    public ApiResponse<List<Map<String, Object>>> recommend(@PathVariable long candidateId) { return ApiResponse.ok(recommendations.recommend(candidateId)); }

    @GetMapping("/providers")
    public ApiResponse<List<Map<String, Object>>> providers() { return ApiResponse.ok(llm.providers()); }
}

