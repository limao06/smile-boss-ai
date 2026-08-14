package com.smileboss.sqlai;

import com.smileboss.common.ApiResponse;
import com.smileboss.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/text-to-sql")
public class TextToSqlController {
    private final TextToSqlService service;

    public TextToSqlController(TextToSqlService service) {
        this.service = service;
    }

    @PostMapping("/queries")
    public ApiResponse<Map<String, Object>> ask(@Valid @RequestBody QueryBody body, HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(service.ask(new TextToSqlService.QueryRequest(
                body.question(), body.execute(), body.maxRows()), userId(request)));
    }

    @GetMapping("/queries")
    public ApiResponse<List<Map<String, Object>>> runs(HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(service.listRuns());
    }

    @GetMapping("/queries/{runId}")
    public ApiResponse<Map<String, Object>> run(@PathVariable long runId, HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(service.run(runId));
    }

    @PostMapping("/queries/{runId}/decisions")
    public ApiResponse<Map<String, Object>> decide(@PathVariable long runId, @Valid @RequestBody DecisionBody body,
                                                   HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(service.decide(runId,
                new TextToSqlService.DecisionRequest(body.decision(), body.comment()), userId(request)));
    }

    @PostMapping("/queries/{runId}/feedback")
    public ApiResponse<Map<String, Object>> feedback(@PathVariable long runId, @Valid @RequestBody FeedbackBody body,
                                                     HttpServletRequest request) {
        requireAdmin(request);
        return ApiResponse.ok(service.feedback(runId,
                new TextToSqlService.FeedbackRequest(body.rating(), body.correctionSql(), body.comment()), userId(request)));
    }

    private static long userId(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new BizException("无法识别当前用户");
    }

    private static void requireAdmin(HttpServletRequest request) {
        if (!"ADMIN".equals(String.valueOf(request.getAttribute("role")))) {
            throw new BizException("只有管理员可以使用智能问数与数据审核接口");
        }
    }

    public record QueryBody(
            @NotBlank String question,
            Boolean execute,
            @Min(1) @Max(1000) Integer maxRows) {}

    public record DecisionBody(@NotBlank String decision, String comment) {}

    public record FeedbackBody(
            @Min(1) @Max(5) Integer rating,
            String correctionSql,
            String comment) {}
}
