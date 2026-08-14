package com.smileboss.auth;

import com.smileboss.common.ApiResponse;
import com.smileboss.common.BizException;
import com.smileboss.common.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;

    public AuthController(JdbcTemplate jdbcTemplate, JwtService jwtService) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest body) {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id,username,display_name,role FROM sys_user "
                        + "WHERE username=? AND password=? AND status=1",
                body.username(),
                body.password()
        );
        if (users.isEmpty()) {
            throw new BizException("用户名或密码错误");
        }
        Map<String, Object> user = new LinkedHashMap<>(users.get(0));
        long id = ((Number) user.get("id")).longValue();
        String username = String.valueOf(user.get("username"));
        String role = String.valueOf(user.get("role"));
        user.put("token", jwtService.create(id, username, role));
        return ApiResponse.ok(user);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
        return ApiResponse.ok(Map.of(
                "id", request.getAttribute("userId"),
                "username", request.getAttribute("username"),
                "role", request.getAttribute("role")
        ));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }
}
