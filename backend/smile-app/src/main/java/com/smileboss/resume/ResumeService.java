package com.smileboss.resume;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smileboss.common.BizException;
import com.smileboss.persistence.GeneratedKeyExtractor;
import com.smileboss.security.HashUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 简历应用服务：编排文件提取、结构化分析和短事务持久化。 */
@Service
public class ResumeService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ResumeFileExtractor fileExtractor;
    private final ResumeAnalyzer resumeAnalyzer;
    private final ResumeWorkspaceService workspaceService;
    private final TransactionTemplate transactionTemplate;

    public ResumeService(JdbcTemplate jdbcTemplate,
                         ObjectMapper objectMapper,
                         ResumeFileExtractor fileExtractor,
                         ResumeAnalyzer resumeAnalyzer,
                         ResumeWorkspaceService workspaceService,
                         TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.fileExtractor = fileExtractor;
        this.resumeAnalyzer = resumeAnalyzer;
        this.workspaceService = workspaceService;
        this.transactionTemplate = transactionTemplate;
    }

    public Map<String, Object> upload(long candidateId, MultipartFile file) {
        Map<String, Object> candidate = findCandidate(candidateId);
        ResumeFileExtractor.PreparedResume resume = fileExtractor.prepare(file);
        rejectDuplicate(candidateId, resume.hash());
        ResumeAnalyzer.AnalysisResult analysis = resumeAnalyzer.analyze(candidate, resume.text());
        fileExtractor.archive(resume);
        return persist(candidateId, resume.originalFilename(), resume.hash(), resume.text(), analysis);
    }

    public Map<String, Object> parseText(long candidateId, String text) {
        if (text == null || text.isBlank()) {
            throw new BizException("简历文本不能为空");
        }
        Map<String, Object> candidate = findCandidate(candidateId);
        String cleanedText = ResumeFileExtractor.clean(text);
        String hash = HashUtils.sha256(cleanedText);
        rejectDuplicate(candidateId, hash);
        ResumeAnalyzer.AnalysisResult analysis = resumeAnalyzer.analyze(candidate, cleanedText);
        return persist(candidateId, "在线简历.txt", hash, cleanedText, analysis);
    }

    public Map<String, Object> get(long resumeId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, candidate_id, file_name, parse_status, completeness_score,
                       structured_json, completeness_json, created_at
                FROM talent_resume
                WHERE id=?
                """, resumeId);
        if (rows.isEmpty()) {
            throw new BizException("简历不存在");
        }
        return rows.get(0);
    }

    private Map<String, Object> persist(long candidateId, String filename, String hash, String text,
                                        ResumeAnalyzer.AnalysisResult analysis) {
        Map<String, Object> result = transactionTemplate.execute(status -> {
            long resumeId = insertResume(candidateId, filename, hash, text, analysis);
            updateCandidateProfile(candidateId, analysis.structured());
            Map<String, Object> workspaceImport = workspaceService.importParsedResume(
                    candidateId, resumeId, filename, text, analysis.structured());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("resumeId", resumeId);
            response.put("status", "SUCCESS");
            response.put("structured", analysis.structured());
            response.put("completeness", analysis.completeness());
            response.put("workspaceImport", workspaceImport);
            return response;
        });
        if (result == null) {
            throw new BizException("简历保存事务未返回结果");
        }
        return result;
    }

    private long insertResume(long candidateId, String filename, String hash, String text,
                              ResumeAnalyzer.AnalysisResult analysis) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO talent_resume(
                        candidate_id, file_name, file_hash, raw_text, structured_json,
                        parse_status, completeness_score, completeness_json)
                    VALUES(?,?,?,?,?,'SUCCESS',?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, candidateId);
            statement.setString(2, filename);
            statement.setString(3, hash);
            statement.setString(4, text);
            statement.setString(5, toJson(analysis.structured()));
            statement.setInt(6, intValue(analysis.completeness().get("score")));
            statement.setString(7, toJson(analysis.completeness()));
            return statement;
        }, keyHolder);
        return GeneratedKeyExtractor.extractId(keyHolder);
    }

    private void updateCandidateProfile(long candidateId, Map<String, Object> structured) {
        jdbcTemplate.update("""
                UPDATE talent_candidate
                SET skills=?, profile_summary=?, updated_at=CURRENT_TIMESTAMP
                WHERE id=?
                """, String.join(",", stringList(structured.get("skills"))),
                structured.get("summary"), candidateId);
    }

    private Map<String, Object> findCandidate(long candidateId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, name, city, years_of_experience
                       , phone, email, desired_position, profile_summary
                FROM talent_candidate
                WHERE id=?
                """, candidateId);
        if (rows.isEmpty()) {
            throw new BizException("候选人不存在");
        }
        return rows.get(0);
    }

    private void rejectDuplicate(long candidateId, String hash) {
        List<Map<String, Object>> duplicates = jdbcTemplate.queryForList(
                "SELECT id FROM talent_resume WHERE candidate_id=? AND file_hash=?", candidateId, hash);
        if (!duplicates.isEmpty()) {
            throw new BizException("这份简历已经上传过，简历编号：" + duplicates.get(0).get("id"));
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BizException("简历数据序列化失败：" + exception.getOriginalMessage());
        }
    }

    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static List<String> stringList(Object value) {
        return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }
}
