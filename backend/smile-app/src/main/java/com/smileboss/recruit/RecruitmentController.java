package com.smileboss.recruit;

import com.smileboss.common.ApiResponse;
import com.smileboss.common.BizException;
import com.smileboss.persistence.GeneratedKeyExtractor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RecruitmentController {
    private final JdbcTemplate jdbcTemplate;

    public RecruitmentController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "application", "SmileBoss AI"));
    }

    @GetMapping("/jobs")
    public ApiResponse<List<Map<String, Object>>> jobs(@RequestParam(required = false) String status) {
        return ApiResponse.ok(status == null
                ? jdbcTemplate.queryForList("SELECT * FROM recruit_job ORDER BY id DESC")
                : jdbcTemplate.queryForList("SELECT * FROM recruit_job WHERE status=? ORDER BY id DESC", status));
    }

    @GetMapping("/jobs/{id}")
    public ApiResponse<Map<String, Object>> job(@PathVariable long id) {
        return ApiResponse.ok(one("SELECT * FROM recruit_job WHERE id=?", id));
    }

    @PostMapping("/jobs")
    public ApiResponse<Map<String, Object>> createJob(@Valid @RequestBody JobRequest body) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO recruit_job(title,department,city,salary_min,salary_max,experience_years,"
                            + "required_skills,description,status) VALUES(?,?,?,?,?,?,?,?,?)",
                    new String[]{"id"});
            statement.setString(1, body.title());
            statement.setString(2, body.department());
            statement.setString(3, body.city());
            statement.setObject(4, body.salaryMin());
            statement.setObject(5, body.salaryMax());
            statement.setObject(6, body.experienceYears());
            statement.setString(7, body.requiredSkills());
            statement.setString(8, body.description());
            statement.setString(9, body.status() == null ? "DRAFT" : body.status());
            return statement;
        }, keyHolder);
        return job(GeneratedKeyExtractor.extractId(keyHolder));
    }

    @PutMapping("/jobs/{id}")
    public ApiResponse<Map<String, Object>> updateJob(@PathVariable long id, @Valid @RequestBody JobRequest body) {
        jdbcTemplate.update(
                "UPDATE recruit_job SET title=?,department=?,city=?,salary_min=?,salary_max=?,experience_years=?,"
                        + "required_skills=?,description=?,status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                body.title(), body.department(), body.city(), body.salaryMin(), body.salaryMax(),
                body.experienceYears(), body.requiredSkills(), body.description(), body.status(), id
        );
        return job(id);
    }

    @GetMapping("/candidates")
    public ApiResponse<List<Map<String, Object>>> candidates() {
        return ApiResponse.ok(jdbcTemplate.queryForList("SELECT * FROM talent_candidate ORDER BY id DESC"));
    }

    @GetMapping("/candidates/{id}")
    public ApiResponse<Map<String, Object>> candidate(@PathVariable long id) {
        return ApiResponse.ok(one("SELECT * FROM talent_candidate WHERE id=?", id));
    }

    @PostMapping("/candidates")
    public ApiResponse<Map<String, Object>> createCandidate(@Valid @RequestBody CandidateRequest body) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO talent_candidate(name,phone,email,city,years_of_experience,desired_position,"
                            + "skills,profile_summary) VALUES(?,?,?,?,?,?,?,?)",
                    new String[]{"id"});
            statement.setString(1, body.name());
            statement.setString(2, body.phone());
            statement.setString(3, body.email());
            statement.setString(4, body.city());
            statement.setInt(5, body.yearsOfExperience() == null ? 0 : body.yearsOfExperience());
            statement.setString(6, body.desiredPosition());
            statement.setString(7, body.skills());
            statement.setString(8, body.profileSummary());
            return statement;
        }, keyHolder);
        return candidate(GeneratedKeyExtractor.extractId(keyHolder));
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        if (rows.isEmpty()) {
            throw new BizException("数据不存在");
        }
        return rows.get(0);
    }

    public record JobRequest(@NotBlank String title, String department, String city, Integer salaryMin,
                             Integer salaryMax, Integer experienceYears, String requiredSkills,
                             String description, String status) {
    }
    public record CandidateRequest(@NotBlank String name, String phone, String email, String city,
                                   Integer yearsOfExperience, String desiredPosition, String skills,
                                   String profileSummary) {
    }
}
