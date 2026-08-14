package com.smileboss.interview;

import com.smileboss.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/mock-interviews")
public class MockInterviewController {
    private final InterviewService service;
    public MockInterviewController(InterviewService service) { this.service = service; }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody CreateMock body) { return ApiResponse.ok(service.createMock(body.candidateId(), body.jobId(), body.type())); }
    @PostMapping("/{sessionId}/answers")
    public ApiResponse<Map<String, Object>> answer(@PathVariable long sessionId, @Valid @RequestBody Answer body) { return ApiResponse.ok(service.answerMock(sessionId, body.answer())); }
    @GetMapping("/{sessionId}/report")
    public ApiResponse<Map<String, Object>> report(@PathVariable long sessionId) { return ApiResponse.ok(service.mockReport(sessionId)); }

    public record CreateMock(long candidateId, Long jobId, String type) {}
    public record Answer(@NotBlank String answer) {}
}

