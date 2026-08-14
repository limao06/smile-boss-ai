package com.smileboss.dingtalk;

/** 钉钉机器人拒绝请求或网络投递失败。异常信息不得包含 Webhook 和密钥。 */
public class DingTalkDeliveryException extends RuntimeException {
    public DingTalkDeliveryException(String message) {
        super(message);
    }

    public DingTalkDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
