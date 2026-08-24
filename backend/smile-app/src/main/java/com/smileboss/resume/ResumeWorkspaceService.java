package com.smileboss.resume;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smileboss.common.BizException;
import com.smileboss.persistence.GeneratedKeyExtractor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 在线简历聚合服务。所有写操作追加不可变版本，workspace 仅维护当前已发布主版本指针。
 */
@Service
public class ResumeWorkspaceService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ResumeWorkspaceService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> getOrCreate(long candidateId) {
        Map<String, Object> workspace = findWorkspaceByCandidate(candidateId);
        if (workspace == null) {
            Map<String, Object> candidate = findCandidate(candidateId);
            ResumeContent initial = new ResumeContent(
                    string(candidate.get("name")), string(candidate.get("phone")),
                    string(candidate.get("email")), string(candidate.get("city")),
                    string(candidate.get("desired_position")), integer(candidate.get("years_of_experience")),
                    string(candidate.get("profile_summary")), splitSkills(candidate.get("skills")),
                    "", "", "", "", string(candidate.get("profile_summary")));
            long workspaceId = insertWorkspace(candidateId, initial.name() + "的在线简历");
            long versionId = insertVersion(workspaceId, "MASTER", "PROFILE_INITIALIZATION",
                    null, null, initial, "根据候选人资料创建在线主简历");
            publishVersion(workspaceId, versionId);
            workspace = findWorkspace(workspaceId);
        }
        return workspaceView(workspace);
    }

    /**
     * 解析文件首次导入时直接建立主版本；已有在线简历时只创建待确认版本，不静默覆盖。
     */
    @Transactional
    public Map<String, Object> importParsedResume(long candidateId,
                                                  long sourceResumeId,
                                                  String sourceFilename,
                                                  String sourceText,
                                                  Map<String, Object> structured) {
        Map<String, Object> candidate = findCandidate(candidateId);
        ResumeContent content = ResumeContent.fromImport(candidate, structured, sourceText);
        Map<String, Object> workspace = findWorkspaceByCandidate(candidateId);
        boolean firstImport = workspace == null;
        if (!firstImport && workspace.get("current_version_id") != null) {
            content = currentContent(workspace).mergeImport(content);
        }
        long workspaceId = firstImport
                ? insertWorkspace(candidateId, content.name() + "的在线简历")
                : longValue(workspace.get("id"));
        String sourceType = extensionSourceType(sourceFilename);
        long versionId = insertVersion(workspaceId, "MASTER", sourceType,
                sourceResumeId, null, content, "从 " + sourceFilename + " 导入");
        if (firstImport || workspace.get("current_version_id") == null) {
            publishVersion(workspaceId, versionId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspaceId", workspaceId);
        result.put("versionId", versionId);
        result.put("publishStatus", firstImport ? "PUBLISHED" : "AWAITING_CONFIRMATION");
        result.put("requiresConfirmation", !firstImport);
        result.put("quality", ResumeQualityEvaluator.evaluate(content));
        return result;
    }

    @Transactional
    public Map<String, Object> saveMaster(long candidateId, ResumeContent content, String changeSummary) {
        Map<String, Object> workspace = findWorkspaceByCandidate(candidateId);
        if (workspace == null) {
            getOrCreate(candidateId);
            workspace = findWorkspaceByCandidate(candidateId);
        }
        long workspaceId = longValue(workspace.get("id"));
        long versionId = insertVersion(workspaceId, "MASTER", "MANUAL_EDIT",
                null, null, content, defaultIfBlank(changeSummary, "编辑在线主简历"));
        publishVersion(workspaceId, versionId);
        updateCandidateProjection(candidateId, content);
        return workspaceView(findWorkspace(workspaceId));
    }

    @Transactional
    public Map<String, Object> publishImportedVersion(long candidateId, long versionId) {
        Map<String, Object> workspace = requireWorkspaceByCandidate(candidateId);
        Map<String, Object> version = requireVersion(versionId);
        long workspaceId = longValue(workspace.get("id"));
        if (longValue(version.get("workspace_id")) != workspaceId
                || !"MASTER".equals(string(version.get("version_type")))) {
            throw new BizException("只能发布当前候选人的主简历版本");
        }
        publishVersion(workspaceId, versionId);
        ResumeContent content = parseContent(string(version.get("content_json")));
        updateCandidateProjection(candidateId, content);
        return workspaceView(findWorkspace(workspaceId));
    }

    @Transactional
    public Map<String, Object> createTargetedVersion(long candidateId, long jobId, String templateCode) {
        Map<String, Object> workspace = requireWorkspaceByCandidate(candidateId);
        ResumeContent source = currentContent(workspace);
        Map<String, Object> job = requireJob(jobId);
        Set<String> requiredSkills = splitSkills(job.get("required_skills")).stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String> matchedSkills = source.skills().stream()
                .filter(skill -> requiredSkills.contains(skill.toLowerCase(Locale.ROOT))).toList();
        String highlighted = matchedSkills.isEmpty() ? String.join("、", source.skills()) : String.join("、", matchedSkills);
        String generatedSummary = source.yearsOfExperience() + " 年相关经验，目标岗位为"
                + string(job.get("title")) + "。"
                + (highlighted.isBlank() ? source.summary() : "已确认技能包括 " + highlighted + "。")
                + "内容仅基于在线主简历中的已确认事实。";
        ResumeContent targeted = source.withDesiredPosition(string(job.get("title"))).withSummary(generatedSummary);
        long versionId = insertVersion(longValue(workspace.get("id")), "TARGETED", "ONE_CLICK_GENERATION",
                null, jobId, targeted, "生成岗位定制简历，模板 " + defaultIfBlank(templateCode, "ATS_STANDARD_V1"));
        return versionView(requireVersion(versionId));
    }

    public ResumeContent currentContentForCandidate(long candidateId) {
        return currentContent(requireWorkspaceByCandidate(candidateId));
    }

    public ResumeContent contentOfVersion(long candidateId, long versionId) {
        Map<String, Object> workspace = requireWorkspaceByCandidate(candidateId);
        Map<String, Object> version = requireVersion(versionId);
        if (longValue(version.get("workspace_id")) != longValue(workspace.get("id"))) {
            throw new BizException("简历版本不属于当前候选人");
        }
        return parseContent(string(version.get("content_json")));
    }

    public Map<String, Object> version(long candidateId, long versionId) {
        contentOfVersion(candidateId, versionId);
        return versionView(requireVersion(versionId));
    }

    public byte[] renderPrintableHtml(long candidateId, long versionId) {
        ResumeContent content = contentOfVersion(candidateId, versionId);
        String skills = content.skills().stream().map(ResumeWorkspaceService::escapeHtml)
                .collect(Collectors.joining(" · "));
        String html = """
                <!doctype html><html lang="zh-CN"><head><meta charset="UTF-8">
                <title>%s - 简历</title><style>
                body{font-family:'Microsoft YaHei',Arial,sans-serif;color:#20242b;max-width:820px;margin:36px auto;line-height:1.65;padding:0 28px}
                h1{margin:0;font-size:30px}h2{font-size:18px;border-bottom:2px solid #2f6fed;padding-bottom:6px;margin-top:28px}
                .meta{color:#606773;margin:8px 0 18px}.skills{color:#234e9b}.muted{color:#7b8491}p{white-space:pre-wrap}
                @media print{body{margin:0;max-width:none}button{display:none}}
                </style></head><body>
                <h1>%s</h1><div class="meta">%s · %s · %s · %s</div>
                <h2>个人概述</h2><p>%s</p>
                <h2>专业技能</h2><p class="skills">%s</p>
                <h2>工作经历</h2><p>%s</p>
                <h2>项目经历</h2><p>%s</p>
                <h2>教育经历</h2><p>%s</p>
                <h2>证书与认证</h2><p>%s</p>
                <p class="muted">本文件由 SmileBoss AI 根据候选人已确认信息生成，请在使用前再次核对。</p>
                </body></html>
                """.formatted(
                escapeHtml(content.name()), escapeHtml(content.name()), escapeHtml(content.desiredPosition()),
                escapeHtml(content.city()), escapeHtml(content.phone()), escapeHtml(content.email()),
                escapeHtml(content.summary()), skills,
                displayOrPlaceholder(content.workExperience()), displayOrPlaceholder(content.projectExperience()),
                displayOrPlaceholder(content.education()), displayOrPlaceholder(content.certificates()));
        return html.getBytes(StandardCharsets.UTF_8);
    }

    Map<String, Object> createVersionFromOptimization(long candidateId,
                                                       long workspaceId,
                                                       ResumeContent content,
                                                       String summary) {
        Map<String, Object> workspace = requireWorkspaceByCandidate(candidateId);
        if (longValue(workspace.get("id")) != workspaceId) {
            throw new BizException("优化任务与简历工作台不匹配");
        }
        long versionId = insertVersion(workspaceId, "MASTER", "AI_OPTIMIZATION",
                null, null, content, summary);
        publishVersion(workspaceId, versionId);
        updateCandidateProjection(candidateId, content);
        return versionView(requireVersion(versionId));
    }

    private Map<String, Object> workspaceView(Map<String, Object> workspace) {
        long workspaceId = longValue(workspace.get("id"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", workspaceId);
        result.put("candidateId", workspace.get("candidate_id"));
        result.put("resumeName", workspace.get("resume_name"));
        result.put("status", workspace.get("status"));
        result.put("currentVersionId", workspace.get("current_version_id"));
        if (workspace.get("current_version_id") != null) {
            result.put("currentVersion", versionView(requireVersion(longValue(workspace.get("current_version_id")))));
        }
        List<Map<String, Object>> versions = jdbcTemplate.queryForList("""
                SELECT id, workspace_id, version_no, version_type, source_type, source_resume_id,
                       target_job_id, content_json, quality_score, change_summary, created_at
                FROM candidate_resume_version WHERE workspace_id=? ORDER BY version_no DESC
                """, workspaceId);
        result.put("versions", versions.stream().map(this::versionView).toList());
        return result;
    }

    private Map<String, Object> versionView(Map<String, Object> row) {
        ResumeContent content = parseContent(string(row.get("content_json")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("id"));
        result.put("versionNo", row.get("version_no"));
        result.put("versionType", row.get("version_type"));
        result.put("sourceType", row.get("source_type"));
        result.put("sourceResumeId", row.get("source_resume_id"));
        result.put("targetJobId", row.get("target_job_id"));
        result.put("qualityScore", row.get("quality_score"));
        result.put("quality", ResumeQualityEvaluator.evaluate(content));
        result.put("changeSummary", row.get("change_summary"));
        result.put("createdAt", row.get("created_at"));
        result.put("content", content);
        return result;
    }

    private ResumeContent currentContent(Map<String, Object> workspace) {
        if (workspace.get("current_version_id") == null) {
            throw new BizException("在线主简历还没有已发布版本");
        }
        return parseContent(string(requireVersion(longValue(workspace.get("current_version_id"))).get("content_json")));
    }

    private long insertWorkspace(long candidateId, String name) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO candidate_resume_workspace(candidate_id,resume_name,status)
                    VALUES(?,?,'ACTIVE')
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, candidateId);
            statement.setString(2, defaultIfBlank(name, "在线主简历"));
            return statement;
        }, keys);
        return GeneratedKeyExtractor.extractId(keys);
    }

    private long insertVersion(long workspaceId,
                               String versionType,
                               String sourceType,
                               Long sourceResumeId,
                               Long targetJobId,
                               ResumeContent content,
                               String changeSummary) {
        Integer nextVersion = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version_no),0)+1 FROM candidate_resume_version WHERE workspace_id=?",
                Integer.class, workspaceId);
        int score = integer(ResumeQualityEvaluator.evaluate(content).get("overall"));
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO candidate_resume_version(
                        workspace_id,version_no,version_type,source_type,source_resume_id,target_job_id,
                        content_json,quality_score,change_summary)
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, workspaceId);
            statement.setInt(2, nextVersion == null ? 1 : nextVersion);
            statement.setString(3, versionType);
            statement.setString(4, sourceType);
            statement.setObject(5, sourceResumeId);
            statement.setObject(6, targetJobId);
            statement.setString(7, toJson(content));
            statement.setInt(8, score);
            statement.setString(9, changeSummary);
            return statement;
        }, keys);
        return GeneratedKeyExtractor.extractId(keys);
    }

    private void publishVersion(long workspaceId, long versionId) {
        jdbcTemplate.update("""
                UPDATE candidate_resume_workspace
                SET current_version_id=?, updated_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, versionId, workspaceId);
    }

    private void updateCandidateProjection(long candidateId, ResumeContent content) {
        jdbcTemplate.update("""
                UPDATE talent_candidate
                SET name=?, phone=?, email=?, city=?, years_of_experience=?, desired_position=?,
                    skills=?, profile_summary=?, updated_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, content.name(), content.phone(), content.email(), content.city(),
                content.yearsOfExperience(), content.desiredPosition(), String.join(",", content.skills()),
                content.summary(), candidateId);
    }

    private Map<String, Object> requireWorkspaceByCandidate(long candidateId) {
        Map<String, Object> workspace = findWorkspaceByCandidate(candidateId);
        if (workspace == null) {
            getOrCreate(candidateId);
            workspace = findWorkspaceByCandidate(candidateId);
        }
        return workspace;
    }

    private Map<String, Object> findWorkspaceByCandidate(long candidateId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM candidate_resume_workspace WHERE candidate_id=?", candidateId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findWorkspace(long workspaceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM candidate_resume_workspace WHERE id=?", workspaceId);
        if (rows.isEmpty()) {
            throw new BizException("简历工作台不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> requireVersion(long versionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM candidate_resume_version WHERE id=?", versionId);
        if (rows.isEmpty()) {
            throw new BizException("简历版本不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> findCandidate(long candidateId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM talent_candidate WHERE id=?", candidateId);
        if (rows.isEmpty()) {
            throw new BizException("候选人不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> requireJob(long jobId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM recruit_job WHERE id=? AND status='PUBLISHED'", jobId);
        if (rows.isEmpty()) {
            throw new BizException("目标岗位不存在或未发布");
        }
        return rows.get(0);
    }

    private ResumeContent parseContent(String json) {
        try {
            return objectMapper.readValue(json, ResumeContent.class);
        } catch (JsonProcessingException exception) {
            throw new BizException("简历版本内容损坏：" + exception.getOriginalMessage());
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BizException("简历内容序列化失败：" + exception.getOriginalMessage());
        }
    }

    private static String extensionSourceType(String filename) {
        String lower = string(filename).toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "PDF_IMPORT";
        }
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return "DOCX_IMPORT";
        }
        return "TEXT_IMPORT";
    }

    private static List<String> splitSkills(Object value) {
        if (value == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String skill : String.valueOf(value).split("[,，、;；]+")) {
            if (!skill.isBlank() && !result.contains(skill.trim())) {
                result.add(skill.trim());
            }
        }
        return List.copyOf(result);
    }

    private static String displayOrPlaceholder(String value) {
        return escapeHtml(value == null || value.isBlank() ? "待补充" : value);
    }

    private static String escapeHtml(String value) {
        return string(value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int integer(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(string(value));
    }

    private static long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(string(value));
    }
}
