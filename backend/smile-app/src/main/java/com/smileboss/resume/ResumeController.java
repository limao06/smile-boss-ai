package com.smileboss.resume;

import com.smileboss.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {
    private final ResumeService service;
    private final CandidateResumeAccessGuard accessGuard;

    public ResumeController(ResumeService service, CandidateResumeAccessGuard accessGuard) {
        this.service = service;
        this.accessGuard = accessGuard;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ApiResponse<Map<String, Object>> upload(@RequestParam long candidateId,
                                                   @RequestPart MultipartFile file,
                                                   HttpServletRequest request) {
        accessGuard.verify(request, candidateId);
        return ApiResponse.ok(service.upload(candidateId, file));
    }

    @PostMapping("/parse-text")
    public ApiResponse<Map<String, Object>> parseText(@Valid @RequestBody TextResume body,
                                                      HttpServletRequest request) {
        accessGuard.verify(request, body.candidateId());
        return ApiResponse.ok(service.parseText(body.candidateId(), body.text()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable long id, HttpServletRequest request) {
        Map<String, Object> resume = service.get(id);
        long candidateId = ((Number) resume.get("candidate_id")).longValue();
        accessGuard.verify(request, candidateId);
        return ApiResponse.ok(resume);
    }

    public record TextResume(long candidateId, @NotBlank String text) {}
}
