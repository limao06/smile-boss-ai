package com.smileboss.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 钉钉自定义机器人客户端。
 *
 * <p>签名算法与参考项目完全一致：HMAC-SHA256(timestamp + "\n" + secret)，
 * 然后 Base64 并 URL 编码。客户端只返回业务结果，绝不记录带 access_token 的 URL。</p>
 */
@Component
public class DingTalkRobotClient {
    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final DingTalkProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DingTalkRobotClient(DingTalkProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                .build();
    }

    public void sendMarkdown(String title, String markdownText) {
        if (!properties.isEnabled()) {
            throw new DingTalkDeliveryException("钉钉通知未开启");
        }
        properties.validateEnabledConfiguration();
        long timestamp = System.currentTimeMillis();
        URI endpoint = URI.create(buildSignedWebhookUrl(timestamp));
        String safeTitle = ensureKeyword(title);
        String safeText = ensureKeyword(markdownText);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msgtype", "markdown");
        body.put("markdown", Map.of("title", safeTitle, "text", safeText));

        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new DingTalkDeliveryException("钉钉 HTTP 状态异常：" + response.statusCode());
            }
            JsonNode result = objectMapper.readTree(response.body());
            if (result.path("errcode").asInt(-1) != 0) {
                throw new DingTalkDeliveryException("钉钉拒绝消息：" + sanitize(result.path("errmsg").asText("unknown")));
            }
        } catch (DingTalkDeliveryException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DingTalkDeliveryException("钉钉投递被中断", exception);
        } catch (Exception exception) {
            throw new DingTalkDeliveryException("钉钉投递失败：" + sanitize(exception.getMessage()), exception);
        }
    }

    /** 包级可见，便于对签名兼容性做确定性测试。 */
    String buildSignedWebhookUrl(long timestamp) {
        String webhook = properties.getWebhook().trim();
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            return webhook;
        }
        String stringToSign = timestamp + "\n" + secret;
        String signature = hmacBase64(secret, stringToSign);
        String separator = webhook.contains("?") ? "&" : "?";
        return webhook + separator + "timestamp=" + timestamp + "&sign="
                + URLEncoder.encode(signature, StandardCharsets.UTF_8);
    }

    private String ensureKeyword(String value) {
        String text = value == null ? "" : value;
        String keyword = properties.getKeyword() == null ? "" : properties.getKeyword().trim();
        if (!keyword.isBlank() && !text.contains(keyword)) {
            return text + "\n\n" + keyword;
        }
        return text;
    }

    private static String hmacBase64(String secret, String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new DingTalkDeliveryException("钉钉签名计算失败", exception);
        }
    }

    private static String sanitize(String message) {
        if (message == null || message.isBlank()) {
            return "unknown";
        }
        String normalized = message
                .replaceAll("(?i)access_token=[^&\\s]+", "access_token=***")
                .replaceAll("(?i)sign=[^&\\s]+", "sign=***");
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}
