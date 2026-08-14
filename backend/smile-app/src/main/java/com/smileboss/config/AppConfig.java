package com.smileboss.config;

import com.smileboss.common.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Configuration
public class AppConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfig.class);

    @Bean
    JwtService jwtService(@Value("${smile.jwt-secret:}") String configuredSecret, Environment environment) {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            return new JwtService(configuredSecret);
        }
        boolean developmentProfile = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        if (!developmentProfile) {
            throw new IllegalStateException("JWT_SECRET 未配置；非 dev 环境禁止使用临时签名密钥");
        }
        byte[] randomSecret = new byte[48];
        new SecureRandom().nextBytes(randomSecret);
        LOGGER.warn("JWT_SECRET 未配置：dev 模式已生成仅当前进程有效的随机密钥，重启后现有令牌将失效");
        return new JwtService(Base64.getEncoder().encodeToString(randomSecret));
    }

    @Bean
    WebMvcConfigurer webMvcConfigurer(AuthInterceptor authInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                        .allowedMethods("*").allowedHeaders("*").allowCredentials(true);
            }

            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(authInterceptor).addPathPatterns("/api/**")
                        .excludePathPatterns("/api/auth/login", "/api/health");
            }
        };
    }
}
