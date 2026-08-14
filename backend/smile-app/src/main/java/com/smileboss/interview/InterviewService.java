package com.smileboss.interview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smileboss.activity.ActivityService;
import com.smileboss.ai.LlmGateway;
import com.smileboss.common.BizException;
import com.smileboss.persistence.GeneratedKeyExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AI 面试应用服务。
 *
 * <p>该类只负责编排“校验状态 → 生成问题 → 保存回答 → 计算报告 → 推进状态”。
 * 评分细节位于 {@link InterviewEvaluationService}，模型调用通过 {@link LlmGateway} 隔离。</p>
 */
@Service
public class InterviewService {
    private static final Logger LOGGER = LoggerFactory.getLogger(InterviewService.class);
    private static final int DEFAULT_INTERVIEW_DURATION_MINUTES = 25;
    private static final int MAXIMUM_EVIDENCE_LENGTH = 180;
    private static final Set<String> ALLOWED_REVIEW_DECISIONS = Set.of(
            "NEXT_ROUND", "SUPPLEMENT", "TALENT_POOL", "REJECT"
    );

    private static final List<Question> MOCK_QUESTIONS = List.of(
            new Question("MOTIVATION", "请先做一个两分钟以内的自我介绍，并说明你为什么对这个岗位感兴趣。"),
            new Question("PROJECT_EXPERIENCE", "请选择一个最有代表性的项目，说明业务背景、个人职责、最难的问题和最终结果。"),
            new Question("PROBLEM_SOLVING", "项目上线后出现间歇性性能下降，你会按照什么顺序定位问题？"),
            new Question("CORE_COMPETENCY", "请结合实际经历说明，你如何保证核心业务操作的幂等性和数据一致性。"),
            new Question("COMMUNICATION", "当你与同事对技术方案有明显分歧时，你通常如何推动团队作出决定？")
    );

    private static final List<Question> OFFICIAL_QUESTIONS = List.of(
            new Question("MOTIVATION", "请简要介绍自己，并说明你申请这个职位的主要原因。"),
            new Question("RESUME_VERIFICATION", "请介绍简历中最有代表性的一段经历，明确说明你个人负责的部分。"),
            new Question("CORE_COMPETENCY", "请举例说明你如何运用岗位核心技能解决一个实际问题。"),
            new Question("SYSTEM_DESIGN", "如果现有系统业务量在三个月内增长十倍，你会优先评估和改造哪些部分？"),
            new Question("PROBLEM_SOLVING", "请描述一次线上故障或复杂问题的处理过程，以及你从中学到了什么。"),
            new Question("COMMUNICATION", "请介绍一次跨团队合作经历：目标、分歧、你的行动和最终结果分别是什么？")
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LlmGateway llmGateway;
    private final ActivityService activityService;
    private final InterviewEvaluationService evaluationService;

    public InterviewService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            LlmGateway llmGateway,
            ActivityService activityService,
            InterviewEvaluationService evaluationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.llmGateway = llmGateway;
        this.activityService = activityService;
        this.evaluationService = evaluationService;
    }

    public Map<String, Object> createMock(long candidateId, Long jobId, String interviewType) {
        requireExists("SELECT id FROM talent_candidate WHERE id=?", candidateId, "候选人不存在");
        Question firstQuestion = personalize(MOCK_QUESTIONS.get(0), candidateId, jobId, false);
        String normalizedType = interviewType == null || interviewType.isBlank() ? "GENERAL" : interviewType.trim();

        long sessionId = insert(
                "INSERT INTO mock_interview_session(candidate_id,job_id,interview_type,status,current_round) "
                        + "VALUES(?,?,?,'IN_PROGRESS',1)",
                candidateId,
                jobId,
                normalizedType
        );
        insert(
                "INSERT INTO mock_interview_turn(session_id,round_no,question) VALUES(?,?,?)",
                sessionId,
                1,
                firstQuestion.text()
        );
        activityService.record(candidateId, "MOCK_INTERVIEW_STARTED", "MOCK_INTERVIEW", sessionId, Map.of());

        return Map.of(
                "sessionId", sessionId,
                "status", "IN_PROGRESS",
                "round", 1,
                "question", firstQuestion.text()
        );
    }

    public Map<String, Object> answerMock(long sessionId, String answer) {
        Map<String, Object> session = findOne("SELECT * FROM mock_interview_session WHERE id=?", sessionId);
        requireStatus(session, "IN_PROGRESS", "模拟面试不在进行中");

        int currentRound = toInt(session.get("current_round"));
        int score = evaluationService.score(answer);
        Map<String, Object> evaluation = evaluationService.buildPracticeEvaluation(answer, score);
        jdbcTemplate.update(
                "UPDATE mock_interview_turn SET answer=?,evaluation_json=? WHERE session_id=? AND round_no=?",
                answer,
                toJson(evaluation),
                sessionId,
                currentRound
        );

        if (currentRound >= MOCK_QUESTIONS.size()) {
            return finishMockInterview(sessionId, session);
        }
        return moveToNextMockQuestion(sessionId, session, currentRound, evaluation);
    }

    public Map<String, Object> mockReport(long sessionId) {
        Map<String, Object> session = findOne("SELECT * FROM mock_interview_session WHERE id=?", sessionId);
        requireStatus(session, "COMPLETED", "模拟面试尚未完成");
        return Map.of(
                "session", session,
                "turns", jdbcTemplate.queryForList(
                        "SELECT * FROM mock_interview_turn WHERE session_id=? ORDER BY round_no",
                        sessionId
                )
        );
    }

    public Map<String, Object> createInvitation(
            long candidateId,
            long jobId,
            Integer durationMinutes,
            LocalDateTime expiresAt
    ) {
        requireExists("SELECT id FROM talent_candidate WHERE id=?", candidateId, "候选人不存在");
        Map<String, Object> job = findOne("SELECT title FROM recruit_job WHERE id=?", jobId);
        int normalizedDuration = durationMinutes == null
                ? DEFAULT_INTERVIEW_DURATION_MINUTES
                : durationMinutes;

        long invitationId = insert(
                "INSERT INTO ai_interview_invitation(candidate_id,job_id,title,duration_minutes,expires_at,status) "
                        + "VALUES(?,?,?,?,?,'INVITED')",
                candidateId,
                jobId,
                asString(job.get("title")) + " AI 初面",
                normalizedDuration,
                expiresAt
        );
        return findOne("SELECT * FROM ai_interview_invitation WHERE id=?", invitationId);
    }

    public List<Map<String, Object>> invitations(Long candidateId) {
        if (candidateId == null) {
            return jdbcTemplate.queryForList(
                    "SELECT i.*,c.name candidate_name,j.title job_title FROM ai_interview_invitation i "
                            + "JOIN talent_candidate c ON c.id=i.candidate_id "
                            + "JOIN recruit_job j ON j.id=i.job_id ORDER BY i.id DESC"
            );
        }
        return jdbcTemplate.queryForList(
                "SELECT i.*,j.title job_title FROM ai_interview_invitation i "
                        + "JOIN recruit_job j ON j.id=i.job_id WHERE i.candidate_id=? ORDER BY i.id DESC",
                candidateId
        );
    }

    public Map<String, Object> startOfficial(long invitationId, boolean consent) {
        if (!consent) {
            throw new BizException("开始正式 AI 面试前必须阅读说明并授权");
        }
        Map<String, Object> invitation = findOne(
                "SELECT * FROM ai_interview_invitation WHERE id=?",
                invitationId
        );
        requireStatus(invitation, "INVITED", "邀请状态不允许开始面试");
        validateInvitationExpiration(invitation.get("expires_at"));

        long candidateId = toLong(invitation.get("candidate_id"));
        long jobId = toLong(invitation.get("job_id"));
        Map<String, Object> plan = buildOfficialPlan(invitation.get("duration_minutes"));
        Question firstQuestion = personalize(OFFICIAL_QUESTIONS.get(0), candidateId, jobId, true);

        long sessionId = insert(
                "INSERT INTO ai_interview_session(invitation_id,candidate_id,job_id,status,current_stage,current_round,plan_json) "
                        + "VALUES(?,?,?,'IN_PROGRESS','MOTIVATION',1,?)",
                invitationId,
                candidateId,
                jobId,
                toJson(plan)
        );
        insert(
                "INSERT INTO ai_interview_turn(session_id,round_no,competency,question) VALUES(?,?,?,?)",
                sessionId,
                1,
                firstQuestion.competency(),
                firstQuestion.text()
        );
        jdbcTemplate.update(
                "UPDATE ai_interview_invitation SET status='IN_PROGRESS' WHERE id=?",
                invitationId
        );

        return Map.of(
                "sessionId", sessionId,
                "status", "IN_PROGRESS",
                "plan", plan,
                "round", 1,
                "question", firstQuestion.text(),
                "competency", firstQuestion.competency()
        );
    }

    public Map<String, Object> answerOfficial(long sessionId, String answer) {
        Map<String, Object> session = findOne("SELECT * FROM ai_interview_session WHERE id=?", sessionId);
        requireStatus(session, "IN_PROGRESS", "正式面试不在进行中");

        int currentRound = toInt(session.get("current_round"));
        int score = evaluationService.score(answer);
        String evidence = abbreviate(answer, MAXIMUM_EVIDENCE_LENGTH);
        jdbcTemplate.update(
                "UPDATE ai_interview_turn SET answer=?,score=?,evidence=? WHERE session_id=? AND round_no=?",
                answer,
                score,
                evidence,
                sessionId,
                currentRound
        );

        if (currentRound >= OFFICIAL_QUESTIONS.size()) {
            return finishOfficialInterview(sessionId, session);
        }
        return moveToNextOfficialQuestion(sessionId, session, currentRound);
    }

    public Map<String, Object> officialReport(long sessionId) {
        Map<String, Object> session = findOne("SELECT * FROM ai_interview_session WHERE id=?", sessionId);
        return Map.of(
                "session", session,
                "turns", jdbcTemplate.queryForList(
                        "SELECT * FROM ai_interview_turn WHERE session_id=? ORDER BY round_no",
                        sessionId
                )
        );
    }

    public Map<String, Object> review(long sessionId, String decision, String comment) {
        String normalizedDecision = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_REVIEW_DECISIONS.contains(normalizedDecision)) {
            throw new BizException("不支持的审核结论");
        }

        Map<String, Object> session = findOne("SELECT status FROM ai_interview_session WHERE id=?", sessionId);
        requireStatus(session, "PENDING_REVIEW", "当前面试不在待审核状态");
        jdbcTemplate.update(
                "UPDATE ai_interview_session SET status='REVIEWED',hr_decision=?,hr_comment=? WHERE id=?",
                normalizedDecision,
                comment,
                sessionId
        );
        return officialReport(sessionId);
    }

    private Map<String, Object> finishMockInterview(long sessionId, Map<String, Object> session) {
        Map<String, Object> report = buildMockReport(sessionId);
        jdbcTemplate.update(
                "UPDATE mock_interview_session SET status='COMPLETED',score=?,report_json=?,"
                        + "finished_at=CURRENT_TIMESTAMP WHERE id=?",
                report.get("score"),
                toJson(report),
                sessionId
        );
        long candidateId = toLong(session.get("candidate_id"));
        activityService.record(
                candidateId,
                "MOCK_INTERVIEW_COMPLETED",
                "MOCK_INTERVIEW",
                sessionId,
                Map.of("score", report.get("score"))
        );
        return Map.of("action", "FINISH", "status", "COMPLETED", "report", report);
    }

    private Map<String, Object> moveToNextMockQuestion(
            long sessionId,
            Map<String, Object> session,
            int currentRound,
            Map<String, Object> previousEvaluation
    ) {
        int nextRound = currentRound + 1;
        long candidateId = toLong(session.get("candidate_id"));
        Long jobId = toNullableLong(session.get("job_id"));
        Question nextQuestion = personalize(MOCK_QUESTIONS.get(nextRound - 1), candidateId, jobId, false);

        insert(
                "INSERT INTO mock_interview_turn(session_id,round_no,question) VALUES(?,?,?)",
                sessionId,
                nextRound,
                nextQuestion.text()
        );
        jdbcTemplate.update(
                "UPDATE mock_interview_session SET current_round=? WHERE id=?",
                nextRound,
                sessionId
        );
        return Map.of(
                "action", "NEXT_QUESTION",
                "round", nextRound,
                "question", nextQuestion.text(),
                "previousEvaluation", previousEvaluation,
                "progress", nextRound * 100 / MOCK_QUESTIONS.size()
        );
    }

    private Map<String, Object> finishOfficialInterview(long sessionId, Map<String, Object> session) {
        Map<String, Object> report = buildOfficialReport(sessionId);
        jdbcTemplate.update(
                "UPDATE ai_interview_session SET status='PENDING_REVIEW',current_stage='COMPLETED',"
                        + "ai_score=?,report_json=?,finished_at=CURRENT_TIMESTAMP WHERE id=?",
                report.get("score"),
                toJson(report),
                sessionId
        );
        jdbcTemplate.update(
                "UPDATE ai_interview_invitation SET status='PENDING_REVIEW' WHERE id=?",
                session.get("invitation_id")
        );
        return Map.of(
                "action", "FINISH",
                "status", "PENDING_REVIEW",
                "message", "面试已经完成，等待企业人工审核"
        );
    }

    private Map<String, Object> moveToNextOfficialQuestion(
            long sessionId,
            Map<String, Object> session,
            int currentRound
    ) {
        int nextRound = currentRound + 1;
        Question nextQuestion = personalize(
                OFFICIAL_QUESTIONS.get(nextRound - 1),
                toLong(session.get("candidate_id")),
                toLong(session.get("job_id")),
                true
        );
        insert(
                "INSERT INTO ai_interview_turn(session_id,round_no,competency,question) VALUES(?,?,?,?)",
                sessionId,
                nextRound,
                nextQuestion.competency(),
                nextQuestion.text()
        );
        jdbcTemplate.update(
                "UPDATE ai_interview_session SET current_round=?,current_stage=? WHERE id=?",
                nextRound,
                nextQuestion.competency(),
                sessionId
        );
        return Map.of(
                "action", "NEXT_QUESTION",
                "round", nextRound,
                "question", nextQuestion.text(),
                "competency", nextQuestion.competency(),
                "progress", nextRound * 100 / OFFICIAL_QUESTIONS.size()
        );
    }

    /**
     * 仅向模型发送岗位相关资料，不发送候选人的姓名、电话、邮箱等直接身份信息。
     */
    private Question personalize(Question baseQuestion, long candidateId, Long jobId, boolean official) {
        if (!llmGateway.isEnabled()) {
            return baseQuestion;
        }

        Map<String, Object> candidate = findOne(
                "SELECT skills,profile_summary,desired_position FROM talent_candidate WHERE id=?",
                candidateId
        );
        Map<String, Object> job = jobId == null
                ? Map.of()
                : findOne("SELECT title,required_skills,description FROM recruit_job WHERE id=?", jobId);
        String systemPrompt = "你是一名中立、专业的 AI 面试官。只返回一道中文面试题，不要评分，"
                + "不要询问性别、婚育、年龄、宗教等敏感信息。";
        String userPrompt = "场景=" + (official ? "企业正式 AI 初面" : "候选人模拟面试")
                + "；能力维度=" + baseQuestion.competency()
                + "；候选人岗位资料=" + candidate
                + "；岗位=" + job
                + "；基础问题=" + baseQuestion.text()
                + "。请在保持同等难度的前提下，结合简历与岗位改写问题。";

        String purpose = official ? "OFFICIAL_INTERVIEW_QUESTION" : "MOCK_INTERVIEW_QUESTION";
        String question = llmGateway.chat(systemPrompt, userPrompt, purpose)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse(baseQuestion.text());
        return new Question(baseQuestion.competency(), question);
    }

    private Map<String, Object> buildMockReport(long sessionId) {
        List<Map<String, Object>> turns = jdbcTemplate.queryForList(
                "SELECT id,evaluation_json FROM mock_interview_turn WHERE session_id=? ORDER BY round_no",
                sessionId
        );
        int totalScore = 0;
        int validEvaluationCount = 0;
        for (Map<String, Object> turn : turns) {
            String evaluationJson = asString(turn.get("evaluation_json"));
            if (evaluationJson.isBlank()) {
                continue;
            }
            try {
                JsonNode evaluation = objectMapper.readTree(evaluationJson);
                totalScore += evaluation.path("score").asInt();
                validEvaluationCount++;
            } catch (JsonProcessingException exception) {
                LOGGER.warn("忽略无法解析的模拟面试评价，turnId={}", turn.get("id"), exception);
            }
        }

        int score = validEvaluationCount == 0 ? 0 : totalScore / validEvaluationCount;
        return Map.of(
                "score", score,
                "level", evaluationService.level(score),
                "strengths", List.of("完成了完整的模拟面试流程"),
                "improvements", List.of("继续使用 STAR 结构练习", "为关键经历准备量化数据"),
                "notice", "模拟面试报告由候选人本人使用，不自动进入正式招聘决策"
        );
    }

    private Map<String, Object> buildOfficialReport(long sessionId) {
        List<Map<String, Object>> turns = jdbcTemplate.queryForList(
                "SELECT round_no,competency,question,answer,score,evidence FROM ai_interview_turn "
                        + "WHERE session_id=? ORDER BY round_no",
                sessionId
        );
        int totalScore = turns.stream().mapToInt(turn -> toInt(turn.get("score"))).sum();
        int score = turns.isEmpty() ? 0 : totalScore / turns.size();
        Map<String, Integer> dimensionScores = new LinkedHashMap<>();
        for (Map<String, Object> turn : turns) {
            dimensionScores.put(asString(turn.get("competency")), toInt(turn.get("score")));
        }
        long evidenceCount = turns.stream()
                .filter(turn -> !asString(turn.get("evidence")).isBlank())
                .count();

        return Map.of(
                "score", score,
                "level", evaluationService.level(score),
                "dimensionScores", dimensionScores,
                "evidenceCount", evidenceCount,
                "recommendation", score >= 75
                        ? "建议 HR 查看证据后考虑进入下一轮"
                        : "建议 HR 重点复核能力缺口后再决定",
                "notice", "AI 评价仅供辅助，系统不会自动淘汰或录用候选人"
        );
    }

    private Map<String, Object> buildOfficialPlan(Object durationMinutes) {
        return Map.of(
                "version", 1,
                "durationMinutes", durationMinutes,
                "questionCount", OFFICIAL_QUESTIONS.size(),
                "competencies", List.of("岗位动机", "简历核实", "核心能力", "系统设计", "问题解决", "沟通协作"),
                "policy", "AI 只提供辅助评价，最终结果由 HR 审核"
        );
    }

    private void validateInvitationExpiration(Object expiresAt) {
        if (expiresAt != null && toLocalDateTime(expiresAt).isBefore(LocalDateTime.now())) {
            throw new BizException("面试邀请已经过期");
        }
    }

    private long insert(String sql, Object... arguments) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            return statement;
        }, keyHolder);
        return GeneratedKeyExtractor.extractId(keyHolder);
    }

    private void requireExists(String sql, Object argument, String message) {
        if (jdbcTemplate.queryForList(sql, argument).isEmpty()) {
            throw new BizException(message);
        }
    }

    private Map<String, Object> findOne(String sql, Object... arguments) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, arguments);
        if (rows.isEmpty()) {
            throw new BizException("数据不存在");
        }
        return rows.get(0);
    }

    private void requireStatus(Map<String, Object> entity, String expectedStatus, String message) {
        if (!expectedStatus.equals(asString(entity.get("status")))) {
            throw new BizException(message);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BizException("JSON 序列化失败");
        }
    }

    private static String abbreviate(String value, int maximumLength) {
        if (value == null) {
            return "";
        }
        return value.substring(0, Math.min(value.length(), maximumLength));
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int toInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new BizException("数据编号格式错误");
    }

    private static Long toNullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        throw new BizException("时间格式错误");
    }

    private record Question(String competency, String text) {
    }
}
