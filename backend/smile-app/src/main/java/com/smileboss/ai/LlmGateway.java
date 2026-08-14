package com.smileboss.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LlmGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(LlmGateway.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
    private static final int MAXIMUM_OUTPUT_TOKENS = 4096;

    private final ObjectMapper mapper;
    private final Environment env;
    private final JdbcTemplate jdbcTemplate;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final boolean enabled;
    private final String defaultProvider;

    public LlmGateway(ObjectMapper mapper, Environment env, JdbcTemplate jdbcTemplate,
                      @Value("${smile.llm.enabled:false}") boolean enabled,
                      @Value("${smile.llm.chat-provider:claude}") String defaultProvider) {
        this.mapper = mapper;
        this.env = env;
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
        this.defaultProvider = defaultProvider;
    }

    public Optional<String> chat(String system, String user, String scene) {
        if (!enabled) {
            return Optional.empty();
        }
        List<String> order = new ArrayList<>(List.of(defaultProvider, "qwen", "deepseek", "kimi"));
        order = order.stream().distinct().toList();
        for (String provider : order) {
            Provider providerConfiguration = provider(provider);
            if (!providerConfiguration.ready()) {
                continue;
            }
            long started = System.currentTimeMillis();
            try {
                ObjectNode body = mapper.createObjectNode();
                body.put("model", providerConfiguration.model());
                body.put("temperature", 0.2);
                body.put("max_tokens", MAXIMUM_OUTPUT_TOKENS);
                ArrayNode messages = body.putArray("messages");
                messages.addObject().put("role", "system").put("content", system);
                messages.addObject().put("role", "user").put("content", user);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(trimTrailingSlash(providerConfiguration.baseUrl()) + "/chat/completions"))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Authorization", "Bearer " + providerConfiguration.apiKey())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    throw new IllegalStateException("HTTP " + response.statusCode());
                }
                JsonNode root = mapper.readTree(response.body());
                String content = root.path("choices").path(0).path("message").path("content").asText();
                logModelCall(provider, providerConfiguration.model(), scene,
                        System.currentTimeMillis() - started, "SUCCESS", null);
                if (!content.isBlank()) {
                    return Optional.of(content);
                }
            } catch (Exception exception) {
                logModelCall(provider, providerConfiguration.model(), scene,
                        System.currentTimeMillis() - started, "FAILED", safeError(exception));
                LOGGER.warn("模型调用失败，准备尝试下一供应商，provider={}, model={}, scene={}",
                        provider, providerConfiguration.model(), scene, exception);
            }
        }
        return Optional.empty();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<Map<String, Object>> providers() {
        return List.of("claude", "qwen", "deepseek", "kimi").stream().map(name -> {
            Provider providerConfiguration = provider(name);
            return Map.<String, Object>of(
                    "name", name,
                    "model", providerConfiguration.model(),
                    "ready", providerConfiguration.ready(),
                    "active", name.equals(defaultProvider)
            );
        }).toList();
    }

    private Provider provider(String name) {
        String prefix = "smile.llm.providers." + name + ".";
        return new Provider(
                env.getProperty(prefix + "api-key", ""),
                env.getProperty(prefix + "base-url", ""),
                env.getProperty(prefix + "model", ""));
    }

    /**
     * 调用日志失败不能影响业务降级，但必须留下应用日志，避免可观测性故障被静默吞掉。
     */
    private void logModelCall(
            String provider,
            String model,
            String scene,
            long latencyMilliseconds,
            String status,
            String error
    ) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO ai_model_call_log(provider,model,scene,latency_ms,status,error_message) "
                            + "VALUES(?,?,?,?,?,?)",
                    provider,
                    model,
                    scene,
                    latencyMilliseconds,
                    status,
                    error
            );
        } catch (RuntimeException exception) {
            LOGGER.warn("模型调用审计日志写入失败，provider={}, model={}, scene={}, status={}",
                    provider, model, scene, status, exception);
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String safeError(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }

    private record Provider(String apiKey, String baseUrl, String model) {
        boolean ready() {
            return apiKey != null && !apiKey.isBlank()
                    && baseUrl != null && !baseUrl.isBlank()
                    && model != null && !model.isBlank();
        }
    }
}
