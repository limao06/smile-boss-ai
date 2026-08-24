package com.smileboss.resume;

import com.smileboss.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 防止候选人通过修改 candidateId 读取或修改其他人的简历。 */
@Component
public class CandidateResumeAccessGuard {
    private final JdbcTemplate jdbcTemplate;

    public CandidateResumeAccessGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void verify(HttpServletRequest request, long candidateId) {
        String role = String.valueOf(request.getAttribute("role"));
        if ("ADMIN".equals(role) || "HR".equals(role)) {
            return;
        }
        Object userIdAttribute = request.getAttribute("userId");
        long userId = userIdAttribute instanceof Number number ? number.longValue() : -1L;
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM talent_candidate WHERE id=? AND user_id=?",
                Integer.class, candidateId, userId);
        if (count == null || count == 0) {
            throw new BizException("无权访问该候选人的简历");
        }
    }
}
