package com.smileboss.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DingTalkRobotClientTest {

    @Test
    void signatureMatchesDifyTestRobotAlgorithm() throws Exception {
        DingTalkProperties properties = validProperties();
        properties.setSecret("SEC-test-secret");
        DingTalkRobotClient client = new DingTalkRobotClient(properties, new ObjectMapper());

        long timestamp = 1_700_000_000_123L;
        String signedUrl = client.buildSignedWebhookUrl(timestamp);
        String encodedSign = signedUrl.substring(signedUrl.indexOf("&sign=") + 6);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = Base64.getEncoder().encodeToString(mac.doFinal(
                (timestamp + "\n" + properties.getSecret()).getBytes(StandardCharsets.UTF_8)));
        assertThat(signedUrl).contains("timestamp=" + timestamp).doesNotContain(properties.getSecret());
        assertThat(URLDecoder.decode(encodedSign, StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    void enabledWebhookMustUseOfficialHttpsEndpoint() {
        DingTalkProperties properties = validProperties();
        properties.setWebhook("http://example.test/robot/send?access_token=secret");

        assertThatThrownBy(properties::validateEnabledConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void approvalMessageContainsOnlyMetadataAndSecureDeepLink() {
        DingTalkProperties properties = validProperties();
        properties.setApprovalPageUrl("https://recruit.example.com/review");
        DingTalkApprovalMessageFactory factory = new DingTalkApprovalMessageFactory(properties);

        DingTalkApprovalMessageFactory.Message message = factory.create(Map.of(
                "id", 42L,
                "title", "敏感查询审核",
                "risk_level", "L2",
                "approval_type", "TEXT_TO_SQL",
                "reviewer_group", "DATA_REVIEWER",
                "request_payload_json", "{\"phone\":\"13800000000\",\"sql\":\"select phone\"}"));

        assertThat(message.markdown())
                .contains("approvalId=42", "TEXT_TO_SQL", "DATA_REVIEWER")
                .doesNotContain("13800000000", "select phone", "request_payload_json");
    }

    private static DingTalkProperties validProperties() {
        DingTalkProperties properties = new DingTalkProperties();
        properties.setEnabled(true);
        properties.setWebhook("https://oapi.dingtalk.com/robot/send?access_token=test-token");
        return properties;
    }
}
