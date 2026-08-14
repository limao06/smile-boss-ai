package com.smileboss.agent;

import com.smileboss.common.ApiResponse;
import com.smileboss.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent-platform")
public class AgentPlatformController {
    private final AgentPlatformService platform;
    private final WorkflowEngineService engine;

    public AgentPlatformController(AgentPlatformService platform, WorkflowEngineService engine) {
        this.platform = platform;
        this.engine = engine;
    }

    @GetMapping("/agents")
    public ApiResponse<List<Map<String, Object>>> agents(HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(platform.listAgents());
    }

    @GetMapping("/agents/{code}")
    public ApiResponse<Map<String, Object>> agent(@PathVariable String code, HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(platform.agent(code));
    }

    @PostMapping("/agents")
    public ApiResponse<Map<String, Object>> createAgent(@Valid @RequestBody AgentRequest body, HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(platform.createAgent(new AgentPlatformService.AgentDraft(
                body.code(), body.name(), body.description(), body.systemPrompt(), body.modelPolicy(),
                body.inputSchema(), body.outputSchema(), body.toolPolicy(), body.knowledgeScopes(),
                body.maxModelCalls(), body.timeoutSeconds()), userId(request)));
    }

    @PostMapping("/agents/{code}/versions/{version}/publish")
    public ApiResponse<Map<String, Object>> publishAgent(@PathVariable String code, @PathVariable int version,
                                                         HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(platform.publishAgent(code, version));
    }

    @GetMapping("/workflows")
    public ApiResponse<List<Map<String, Object>>> workflows(HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(platform.listWorkflows());
    }

    @GetMapping("/workflows/{code}")
    public ApiResponse<Map<String, Object>> workflow(@PathVariable String code, HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(platform.workflow(code));
    }

    @PostMapping("/workflows")
    public ApiResponse<Map<String, Object>> createWorkflow(@Valid @RequestBody WorkflowRequest body,
                                                            HttpServletRequest request) {
        requireAdmin(request);
        List<AgentPlatformService.NodeDraft> nodes = body.nodes() == null ? List.of() : body.nodes().stream()
                .map(it -> new AgentPlatformService.NodeDraft(it.code(), it.type(), it.name(), it.config(), it.retry(), it.timeoutSeconds()))
                .toList();
        List<AgentPlatformService.EdgeDraft> edges = body.edges() == null ? List.of() : body.edges().stream()
                .map(it -> new AgentPlatformService.EdgeDraft(it.from(), it.to(), it.condition(), it.priority()))
                .toList();
        return ApiResponse.ok(platform.createWorkflow(new AgentPlatformService.WorkflowDraft(
                body.code(), body.name(), body.description(), body.stateSchema(), body.inputSchema(),
                body.outputSchema(), body.budget(), nodes, edges), userId(request)));
    }

    @PostMapping("/workflows/{code}/versions/{version}/publish")
    public ApiResponse<Map<String, Object>> publishWorkflow(@PathVariable String code, @PathVariable int version,
                                                            HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(platform.publishWorkflow(code, version));
    }

    @PostMapping("/runs")
    public ApiResponse<Map<String, Object>> start(@Valid @RequestBody StartRunRequest body, HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(engine.start(new WorkflowEngineService.StartCommand(
                body.workflowCode(), body.businessType(), body.businessId(), body.input()), userId(request)));
    }

    @GetMapping("/runs")
    public ApiResponse<List<Map<String, Object>>> runs(HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(engine.listRuns());
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<Map<String, Object>> run(@PathVariable long runId, HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(engine.run(runId));
    }

    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<Map<String, Object>> cancel(@PathVariable long runId, HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(engine.cancel(runId, userId(request)));
    }

    @GetMapping("/human-tasks")
    public ApiResponse<List<Map<String, Object>>> humanTasks(HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(engine.pendingHumanTasks());
    }

    @PostMapping("/human-tasks/{approvalId}/claim")
    public ApiResponse<Map<String, Object>> claim(@PathVariable long approvalId,
                                                  @RequestBody(required = false) ClaimRequest body,
                                                  HttpServletRequest request) {
        requireAdmin(request);
        int leaseMinutes = body == null || body.leaseMinutes() == null ? 30 : body.leaseMinutes();
        return ApiResponse.ok(engine.claim(approvalId, leaseMinutes, userId(request)));
    }

    @PostMapping("/human-tasks/{taskId}/decisions")
    public ApiResponse<Map<String, Object>> decide(@PathVariable long taskId, @Valid @RequestBody DecisionRequest body,
                                                   HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(engine.decide(taskId, body.decision(), body.comment(), userId(request)));
    }

    private static long userId(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (value instanceof Number number) return number.longValue();
        throw new BizException("无法识别当前用户");
    }

    private static void requireAdmin(HttpServletRequest request) {
        if (!"ADMIN".equals(String.valueOf(request.getAttribute("role")))) {
            throw new BizException("只有管理员可以使用多 Agent 协作平台管理接口");
        }
    }

    public record AgentRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            @NotBlank String systemPrompt,
            String modelPolicy,
            Map<String, Object> inputSchema,
            Map<String, Object> outputSchema,
            Map<String, Object> toolPolicy,
            List<String> knowledgeScopes,
            Integer maxModelCalls,
            Integer timeoutSeconds) {}

    public record WorkflowRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            Map<String, Object> stateSchema,
            Map<String, Object> inputSchema,
            Map<String, Object> outputSchema,
            Map<String, Object> budget,
            List<NodeRequest> nodes,
            List<EdgeRequest> edges) {}

    public record NodeRequest(
            @NotBlank String code,
            @NotBlank String type,
            String name,
            Map<String, Object> config,
            Map<String, Object> retry,
            Integer timeoutSeconds) {}

    public record EdgeRequest(
            @NotBlank String from,
            @NotBlank String to,
            String condition,
            Integer priority) {}

    public record StartRunRequest(
            @NotBlank String workflowCode,
            String businessType,
            String businessId,
            Map<String, Object> input) {}

    public record DecisionRequest(@NotBlank String decision, String comment) {}
    public record ClaimRequest(Integer leaseMinutes) {}
}
