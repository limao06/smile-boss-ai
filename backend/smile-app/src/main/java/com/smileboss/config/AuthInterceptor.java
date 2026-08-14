package com.smileboss.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smileboss.common.ApiResponse;
import com.smileboss.common.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            unauthorized(response, "请先登录");
            return false;
        }
        try {
            Claims claims = jwtService.parse(header.substring(7));
            request.setAttribute("userId", Long.parseLong(claims.getSubject()));
            request.setAttribute("username", claims.get("username", String.class));
            request.setAttribute("role", claims.get("role", String.class));
            return true;
        } catch (RuntimeException exception) {
            unauthorized(response, "登录状态已失效");
            return false;
        }
    }

    private void unauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(message)));
    }
}
