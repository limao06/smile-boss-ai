package com.smileboss.interview;

import com.smileboss.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-interviews")
public class AiInterviewController {
    private final InterviewService service;
    public AiInterviewController(InterviewService service) { this.service = service; }

    @PostMapping("/invitations")
    public ApiResponse<Map<String, Object>> invite(@RequestBody Invite body) { return ApiResponse.ok(service.createInvitation(body.candidateId(), body.jobId(), body.durationMinutes(), body.expiresAt())); }
    @GetMapping("/invitations")
    public ApiResponse<List<Map<String, Object>>> invitations(@RequestParam(required = false) Long candidateId) { return ApiResponse.ok(service.invitations(candidateId)); }
    @PostMapping("/invitations/{invitationId}/start")
    public ApiResponse<Map<String, Object>> start(@PathVariable long invitationId, @RequestBody Start body) { return ApiResponse.ok(service.startOfficial(invitationId, body.consent())); }
    @PostMapping("/sessions/{sessionId}/answers")
    public ApiResponse<Map<String, Object>> answer(@PathVariable long sessionId, @Valid @RequestBody Answer body) { return ApiResponse.ok(service.answerOfficial(sessionId, body.answer())); }
    @GetMapping("/sessions/{sessionId}/report")
    public ApiResponse<Map<String, Object>> report(@PathVariable long sessionId) { return ApiResponse.ok(service.officialReport(sessionId)); }
    @PostMapping("/sessions/{sessionId}/review")
    public ApiResponse<Map<String, Object>> review(@PathVariable long sessionId, @RequestBody Review body) { return ApiResponse.ok(service.review(sessionId, body.decision(), body.comment())); }

    public record Invite(long candidateId, long jobId, Integer durationMinutes, LocalDateTime expiresAt) {}
    public record Start(boolean consent) {}
    public record Answer(@NotBlank String answer) {}
    public record Review(@NotBlank String decision, String comment) {}
}

