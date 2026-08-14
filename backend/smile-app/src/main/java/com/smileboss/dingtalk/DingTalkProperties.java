package com.smileboss.dingtalk;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.Set;

/**
 * 钉钉人工审核通知配置。
 *
 * <p>环境变量名称与参考实现保持一致：
 * {@code DINGTALK_WEBHOOK}、{@code DINGTALK_SECRET}、{@code DINGTALK_KEYWORD}。
 * 额外的审批工作台地址只承载审批编号，不携带简历、SQL 等敏感内容。</p>
 */
@Configuration
@ConfigurationProperties(prefix = "smile.dingtalk")
public class DingTalkProperties {
    private static final Set<String> OFFICIAL_ROBOT_HOSTS = Set.of("oapi.dingtalk.com");

    private boolean enabled;
    private String webhook = "";
    private String secret = "";
    private String keyword = "招聘审核";
    private String approvalPageUrl = "http://localhost:5173";
    private int connectTimeoutSeconds = 5;
    private int requestTimeoutSeconds = 15;
    private int dispatchIntervalMillis = 5_000;
    private int initialDelayMillis = 3_000;
    private int batchSize = 20;
    private int maxRetries = 5;
    private int retryBaseSeconds = 30;
    private int retryMaximumSeconds = 3_600;
    private int staleLockSeconds = 120;

    /** 开启通知时进行严格校验，避免错误配置导致审批通知静默丢失。 */
    public void validateEnabledConfiguration() {
        if (!enabled) {
            return;
        }
        if (webhook == null || webhook.isBlank()) {
            throw new IllegalStateException("DINGTALK_ENABLED=true 时必须配置 DINGTALK_WEBHOOK");
        }
        URI uri;
        try {
            uri = URI.create(webhook.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("DINGTALK_WEBHOOK 不是合法 URL", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("DINGTALK_WEBHOOK 必须使用 HTTPS");
        }
        if (!OFFICIAL_ROBOT_HOSTS.contains(uri.getHost())) {
            throw new IllegalStateException("DINGTALK_WEBHOOK 必须指向钉钉官方机器人域名");
        }
        if (uri.getRawQuery() == null || !uri.getRawQuery().contains("access_token=")) {
            throw new IllegalStateException("DINGTALK_WEBHOOK 缺少 access_token");
        }
        if (approvalPageUrl == null || approvalPageUrl.isBlank()) {
            throw new IllegalStateException("DINGTALK_APPROVAL_PAGE_URL 不能为空");
        }
        validateApprovalPageUrl();
    }

    public String approvalUrl(long approvalId) {
        String base = approvalPageUrl == null ? "" : approvalPageUrl.trim();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "approvalId=" + approvalId;
    }

    private void validateApprovalPageUrl() {
        URI uri;
        try {
            uri = URI.create(approvalPageUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("DINGTALK_APPROVAL_PAGE_URL 不是合法 URL", exception);
        }
        boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !localHttp) {
            throw new IllegalStateException("DINGTALK_APPROVAL_PAGE_URL 生产环境必须使用 HTTPS");
        }
        if (uri.getFragment() != null) {
            throw new IllegalStateException("DINGTALK_APPROVAL_PAGE_URL 不能包含 URL fragment");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getWebhook() { return webhook; }
    public void setWebhook(String webhook) { this.webhook = webhook; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getApprovalPageUrl() { return approvalPageUrl; }
    public void setApprovalPageUrl(String approvalPageUrl) { this.approvalPageUrl = approvalPageUrl; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int value) { this.connectTimeoutSeconds = value; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int value) { this.requestTimeoutSeconds = value; }
    public int getDispatchIntervalMillis() { return dispatchIntervalMillis; }
    public void setDispatchIntervalMillis(int value) { this.dispatchIntervalMillis = value; }
    public int getInitialDelayMillis() { return initialDelayMillis; }
    public void setInitialDelayMillis(int value) { this.initialDelayMillis = value; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int value) { this.batchSize = value; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int value) { this.maxRetries = value; }
    public int getRetryBaseSeconds() { return retryBaseSeconds; }
    public void setRetryBaseSeconds(int value) { this.retryBaseSeconds = value; }
    public int getRetryMaximumSeconds() { return retryMaximumSeconds; }
    public void setRetryMaximumSeconds(int value) { this.retryMaximumSeconds = value; }
    public int getStaleLockSeconds() { return staleLockSeconds; }
    public void setStaleLockSeconds(int value) { this.staleLockSeconds = value; }
}
