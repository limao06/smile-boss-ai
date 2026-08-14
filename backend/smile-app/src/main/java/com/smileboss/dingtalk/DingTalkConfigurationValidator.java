package com.smileboss.dingtalk;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/** 在应用接收流量前完成钉钉配置校验。 */
@Component
public class DingTalkConfigurationValidator implements SmartInitializingSingleton {
    private final DingTalkProperties properties;

    public DingTalkConfigurationValidator(DingTalkProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        properties.validateEnabledConfiguration();
    }
}
