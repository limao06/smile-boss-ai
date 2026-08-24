package com.smileboss.resume;

import com.smileboss.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 候选人简历工作台 API：主简历、版本、优化、岗位简历和招呼语统一入口。 */
@RestController
@RequestMapping("/api/candidates/{candidateId}/resume-workspace")
public class ResumeWorkspaceController {
    private final CandidateResumeAccessGuard accessGuard;
    private final ResumeWorkspaceService workspaceService;
    private final ResumeCopilotService copilotService;

    public ResumeWorkspaceController(CandidateResumeAccessGuard accessGuard,
                                     ResumeWorkspaceService workspaceService,
                                     ResumeCopilotService copilotService) {
        this.accessGuard = accessGuard;
        this.workspaceService = workspaceService;
        this.copilotService = copilotService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> workspace(@PathVariable long candidateId,
                                                      HttpServletRequest request) {
        accessGuard.verify(request, candidateId);
        return ApiResponse.ok(workspaceService.getOrCreate(candidateId));
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> save(@PathVariable long candidateId,
                                                 @Valid @RequestBody SaveResumeRequest body,
                                                 HttpServletRequest request) {
        accessGuard.verify(request, candidateId);
        return ApiResponse.ok(workspaceService.saveMaster(candidateId, body.content(), body.changeSummary()));
    }

    @PostMapping("/versions/{versionId}/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable long candidateId,
                                                    @PathVariable long versionId,
                                                    HttpServletRequest request) {
        accessGuard.verify(request, candidateId);
        return ApiResponse.ok(workspaceService.publishImportedVersion(candidateId, versionId));
    }

    @PostMapping("/optimize")
    public ApiResponse<Map<String, Object>> optimize(@PathVariable long candidateId,
                                                     @RequestBody(required = false) OptimizeRequest body,
                                                     HttpServletRequest request) {
        accessGuard.verify(request, candidateId);
        Long targetJobId = body == null ? null : body.targetJobId();
        return ApiResponse.ok(copilotService.optimize(candidateId, targetJobId));
    }

    @GetMapping("/optimizations/{taskId}")
    public ApiResponse<Map<String, Object>> optimization(@PathVariable long candidateId,
                                                         @PathVariable long taskId,
                                                         HttpServletRequest request) {
        accessGuard.verify(request, candidateId);
        return ApiResponse.ok(copilotService.optimization(taskId, candidateId));
    }

    @PostMapping("/optimizations/{taskId}/apply")
    public ApiResponse<Map<String, Object>> applyOptimization(@PathVariable long candidateId,
                                                              @PathVariable long taskId,
                                                              @RequestBody ApplyOptimizationRequest body,
                                                              HttpServletRequest request) {
        accessGuard.verify(request, candidateId);
        return ApiResponse.ok(copilotService.applyOptimization(taskId, candidateId, body.suggestionIds()));
    }

    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generate(@PathVariable long candidateId,
                                                     @Valid @RequestBody GenerateResumeRequest body,
                                                     HttpServletRequest request) {
        accessGuard.verify(request, candidateId);
        return ApiResponse.ok(workspaceService.createTargetedVersion(
                candidateId, body.targetJobId(), body.templateCode()));
    }

    @GetMapping("/versions/{versionId}/export")
    public ResponseEntity<byte[]> export(@PathVariable long candidateId,
                                         @PathVariable long versionId,
                                         HttpServletRequest request) {
        accessGuard.verify(request, candidateId);
        byte[] content = workspaceService.renderPrintableHtml(candidateId, versionId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("resume-v" + versionId + ".html", StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(content);
    }

    @PostMapping("/greetings")
    public ApiResponse<Map<String, Object>> greetings(@PathVariable long candidateId,
                                                      @Valid @RequestBody GreetingRequest body,
                                                      HttpServletRequest request) {
        accessGuard.verify(request, candidateId);
        return ApiResponse.ok(copilotService.generateGreetings(
                candidateId, body.jobId(), body.versionId(), body.tone(), body.maximumCharacters()));
    }

    public record SaveResumeRequest(@NotNull ResumeContent content, String changeSummary) {
    }

    public record OptimizeRequest(Long targetJobId) {
    }

    public record ApplyOptimizationRequest(List<String> suggestionIds) {
    }

    public record GenerateResumeRequest(@NotNull Long targetJobId, String templateCode) {
    }

    public record GreetingRequest(@NotNull Long jobId,
                                  Long versionId,
                                  String tone,
                                  Integer maximumCharacters) {
    }
}
