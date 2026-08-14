package com.smileboss.resume;

import com.smileboss.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {
    private final ResumeService service;
    public ResumeController(ResumeService service) { this.service = service; }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ApiResponse<Map<String, Object>> upload(@RequestParam long candidateId, @RequestPart MultipartFile file) {
        return ApiResponse.ok(service.upload(candidateId, file));
    }

    @PostMapping("/parse-text")
    public ApiResponse<Map<String, Object>> parseText(@Valid @RequestBody TextResume body) {
        return ApiResponse.ok(service.parseText(body.candidateId(), body.text()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable long id) { return ApiResponse.ok(service.get(id)); }

    public record TextResume(long candidateId, @NotBlank String text) {}
}

