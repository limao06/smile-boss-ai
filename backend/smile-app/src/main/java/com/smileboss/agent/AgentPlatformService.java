package com.smileboss.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smileboss.common.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AgentPlatformService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentPlatformService.class);
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,95}");
    private static final Set<String> NODE_TYPES = Set.of(
            "START", "AGENT", "RULE", "ROUTER", "JOIN", "HUMAN_APPROVAL", "END");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AgentPlatformService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public List<Map<String, Object>> listAgents() {
        return jdbc.queryForList("""
                SELECT d.agent_code,d.name,d.description,d.status,d.current_version,
                       v.model_policy,v.tool_policy,v.knowledge_scopes,v.max_model_calls,v.timeout_seconds,
                       d.created_at,d.updated_at
                FROM ai_agent_definition d
                LEFT JOIN ai_agent_version v ON v.agent_code=d.agent_code AND v.version=d.current_version
                ORDER BY d.agent_code
                """).stream().map(this::decodeJsonColumns).toList();
    }

    public Map<String, Object> agent(String code) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT d.*,v.system_prompt,v.model_policy,v.input_schema,v.output_schema,v.tool_policy,
                       v.knowledge_scopes,v.max_model_calls,v.timeout_seconds,v.status version_status
                FROM ai_agent_definition d
                JOIN ai_agent_version v ON v.agent_code=d.agent_code AND v.version=d.current_version
                WHERE d.agent_code=?
                """, normalizeCode(code));
        if (rows.isEmpty()) throw new BizException("Agent 不存在");
        return decodeJsonColumns(rows.get(0));
    }

    @Transactional
    public Map<String, Object> createAgent(AgentDraft draft, long userId) {
        String code = normalizeCode(draft.code());
        requireCode(code, "Agent 编码");
        if (blank(draft.name()) || blank(draft.systemPrompt())) throw new BizException("Agent 名称和 systemPrompt 不能为空");
        if (count("SELECT COUNT(*) FROM ai_agent_definition WHERE agent_code=?", code) > 0) {
            throw new BizException("Agent 编码已经存在");
        }
        int maxCalls = bounded(draft.maxModelCalls(), 2, 1, 10, "maxModelCalls");
        int timeout = bounded(draft.timeoutSeconds(), 120, 5, 600, "timeoutSeconds");
        jdbc.update("INSERT INTO ai_agent_definition(agent_code,name,description,status,current_version,created_by) VALUES(?,?,?,'DRAFT',1,?)",
                code, draft.name().trim(), value(draft.description()), userId);
        jdbc.update("""
                INSERT INTO ai_agent_version(agent_code,version,system_prompt,model_policy,input_schema,output_schema,
                  tool_policy,knowledge_scopes,max_model_calls,timeout_seconds,status)
                VALUES(?,1,?,?,?,?,?,?,?,?,'DRAFT')
                """, code, draft.systemPrompt().trim(), defaultValue(draft.modelPolicy(), "HIGH_ACCURACY_REASONING"),
                json(draft.inputSchema()), json(draft.outputSchema()), json(draft.toolPolicy()),
                json(draft.knowledgeScopes() == null ? List.of() : draft.knowledgeScopes()), maxCalls, timeout);
        return agent(code);
    }

    @Transactional
    public Map<String, Object> publishAgent(String code, int version) {
        code = normalizeCode(code);
        if (count("SELECT COUNT(*) FROM ai_agent_version WHERE agent_code=? AND version=?", code, version) == 0) {
            throw new BizException("Agent 版本不存在");
        }
        jdbc.update("UPDATE ai_agent_version SET status='PUBLISHED' WHERE agent_code=? AND version=?", code, version);
        jdbc.update("UPDATE ai_agent_definition SET status='PUBLISHED',current_version=?,updated_at=CURRENT_TIMESTAMP WHERE agent_code=?", version, code);
        return agent(code);
    }

    public List<Map<String, Object>> listWorkflows() {
        return jdbc.queryForList("""
                SELECT workflow_code,name,description,status,current_version,created_by,created_at,updated_at
                FROM ai_workflow_definition ORDER BY workflow_code
                """);
    }

    public Map<String, Object> workflow(String code) {
        code = normalizeCode(code);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT d.*,v.state_schema,v.input_schema,v.output_schema,v.budget_json,v.status version_status
                FROM ai_workflow_definition d
                JOIN ai_workflow_version v ON v.workflow_code=d.workflow_code AND v.version=d.current_version
                WHERE d.workflow_code=?
                """, code);
        if (rows.isEmpty()) throw new BizException("工作流不存在");
        Map<String, Object> result = new LinkedHashMap<>(decodeJsonColumns(rows.get(0)));
        int version = number(result.get("current_version"));
        result.put("nodes", jdbc.queryForList("""
                SELECT node_code,node_type,name,config_json,retry_json,timeout_seconds
                FROM ai_workflow_node WHERE workflow_code=? AND workflow_version=? ORDER BY id
                """, code, version).stream().map(this::decodeJsonColumns).toList());
        result.put("edges", jdbc.queryForList("""
                SELECT from_node,to_node,condition_expression,priority
                FROM ai_workflow_edge WHERE workflow_code=? AND workflow_version=? ORDER BY priority,id
                """, code, version));
        return result;
    }

    @Transactional
    public Map<String, Object> createWorkflow(WorkflowDraft draft, long userId) {
        String code = normalizeCode(draft.code());
        requireCode(code, "工作流编码");
        if (blank(draft.name())) throw new BizException("工作流名称不能为空");
        if (count("SELECT COUNT(*) FROM ai_workflow_definition WHERE workflow_code=?", code) > 0) {
            throw new BizException("工作流编码已经存在");
        }
        validateGraph(draft.nodes(), draft.edges());
        jdbc.update("INSERT INTO ai_workflow_definition(workflow_code,name,description,status,current_version,created_by) VALUES(?,?,?,'DRAFT',1,?)",
                code, draft.name().trim(), value(draft.description()), userId);
        jdbc.update("""
                INSERT INTO ai_workflow_version(workflow_code,version,state_schema,input_schema,output_schema,budget_json,status)
                VALUES(?,1,?,?,?,?,'DRAFT')
                """, code, json(draft.stateSchema()), json(draft.inputSchema()), json(draft.outputSchema()), json(draft.budget()));
        for (NodeDraft node : draft.nodes()) {
            String nodeCode = normalizeCode(node.code());
            String type = normalizeCode(node.type());
            jdbc.update("""
                    INSERT INTO ai_workflow_node(workflow_code,workflow_version,node_code,node_type,name,config_json,retry_json,timeout_seconds)
                    VALUES(?,1,?,?,?,?,?,?)
                    """, code, nodeCode, type, defaultValue(node.name(), nodeCode), json(node.config()), json(node.retry()),
                    bounded(node.timeoutSeconds(), 120, 5, 86400, "节点 timeoutSeconds"));
        }
        int priority = 10;
        for (EdgeDraft edge : draft.edges()) {
            jdbc.update("""
                    INSERT INTO ai_workflow_edge(workflow_code,workflow_version,from_node,to_node,condition_expression,priority)
                    VALUES(?,1,?,?,?,?)
                    """, code, normalizeCode(edge.from()), normalizeCode(edge.to()),
                    defaultValue(edge.condition(), "ALWAYS"), edge.priority() == null ? priority : edge.priority());
            priority += 10;
        }
        return workflow(code);
    }

    @Transactional
    public Map<String, Object> publishWorkflow(String code, int version) {
        code = normalizeCode(code);
        if (count("SELECT COUNT(*) FROM ai_workflow_version WHERE workflow_code=? AND version=?", code, version) == 0) {
            throw new BizException("工作流版本不存在");
        }
        List<Map<String, Object>> agentNodes = jdbc.queryForList("""
                SELECT config_json FROM ai_workflow_node
                WHERE workflow_code=? AND workflow_version=? AND node_type='AGENT'
                """, code, version);
        for (Map<String, Object> row : agentNodes) {
            Map<String, Object> config = map(row.get("config_json"));
            String agentCode = normalizeCode(String.valueOf(config.getOrDefault("agentCode", "")));
            if (agentCode.isBlank() || count("SELECT COUNT(*) FROM ai_agent_definition WHERE agent_code=? AND status='PUBLISHED'", agentCode) == 0) {
                throw new BizException("工作流引用了未发布的 Agent：" + agentCode);
            }
        }
        jdbc.update("UPDATE ai_workflow_version SET status='PUBLISHED' WHERE workflow_code=? AND version=?", code, version);
        jdbc.update("UPDATE ai_workflow_definition SET status='PUBLISHED',current_version=?,updated_at=CURRENT_TIMESTAMP WHERE workflow_code=?", version, code);
        return workflow(code);
    }

    private void validateGraph(List<NodeDraft> nodes, List<EdgeDraft> edges) {
        if (nodes == null || nodes.isEmpty()) throw new BizException("工作流至少需要一个节点");
        if (edges == null) edges = List.of();
        Map<String, String> types = new LinkedHashMap<>();
        for (NodeDraft node : nodes) {
            String code = normalizeCode(node.code());
            String type = normalizeCode(node.type());
            requireCode(code, "节点编码");
            if (!NODE_TYPES.contains(type)) throw new BizException("不支持的节点类型：" + type);
            if (types.put(code, type) != null) throw new BizException("节点编码重复：" + code);
            Object agentCode = optionalMap(node.config()).get("agentCode");
            if ("AGENT".equals(type) && (agentCode == null || blank(String.valueOf(agentCode)))) {
                throw new BizException("AGENT 节点必须配置 agentCode：" + code);
            }
        }
        if (types.values().stream().filter("START"::equals).count() != 1) throw new BizException("工作流必须且只能有一个 START 节点");
        if (types.values().stream().noneMatch("END"::equals)) throw new BizException("工作流至少需要一个 END 节点");

        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        types.keySet().forEach(it -> indegree.put(it, 0));
        for (EdgeDraft edge : edges) {
            String from = normalizeCode(edge.from());
            String to = normalizeCode(edge.to());
            if (!types.containsKey(from) || !types.containsKey(to)) throw new BizException("工作流边引用了不存在的节点：" + from + " -> " + to);
            if (from.equals(to)) throw new BizException("MVP 工作流不允许节点自循环：" + from);
            outgoing.computeIfAbsent(from, key -> new ArrayList<>()).add(to);
            indegree.put(to, indegree.get(to) + 1);
        }
        Deque<String> queue = new ArrayDeque<>();
        indegree.forEach((key, value) -> { if (value == 0) queue.add(key); });
        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            visited++;
            for (String next : outgoing.getOrDefault(current, List.of())) {
                indegree.put(next, indegree.get(next) - 1);
                if (indegree.get(next) == 0) queue.add(next);
            }
        }
        if (visited != types.size()) throw new BizException("MVP 工作流暂不支持环；请使用审核修订子工作流或后续版本的有限循环节点");

        String start = types.entrySet().stream().filter(it -> "START".equals(it.getValue()))
                .map(Map.Entry::getKey).findFirst().orElseThrow();
        Set<String> reachable = new LinkedHashSet<>();
        Deque<String> forward = new ArrayDeque<>(List.of(start));
        while (!forward.isEmpty()) {
            String current = forward.removeFirst();
            if (!reachable.add(current)) continue;
            forward.addAll(outgoing.getOrDefault(current, List.of()));
        }
        if (reachable.size() != types.size()) {
            Set<String> missing = new LinkedHashSet<>(types.keySet());
            missing.removeAll(reachable);
            throw new BizException("存在从 START 无法到达的节点：" + missing);
        }

        Map<String, List<String>> incoming = new HashMap<>();
        outgoing.forEach((from, targets) -> targets.forEach(to -> incoming.computeIfAbsent(to, key -> new ArrayList<>()).add(from)));
        Set<String> canReachEnd = new LinkedHashSet<>();
        Deque<String> reverse = new ArrayDeque<>();
        types.forEach((node, type) -> { if ("END".equals(type)) reverse.add(node); });
        while (!reverse.isEmpty()) {
            String current = reverse.removeFirst();
            if (!canReachEnd.add(current)) continue;
            reverse.addAll(incoming.getOrDefault(current, List.of()));
        }
        if (canReachEnd.size() != types.size()) {
            Set<String> deadEnds = new LinkedHashSet<>(types.keySet());
            deadEnds.removeAll(canReachEnd);
            throw new BizException("存在无法到达 END 的节点：" + deadEnds);
        }
    }

    private Map<String, Object> decodeJsonColumns(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        for (String key : List.of("input_schema", "output_schema", "state_schema", "budget_json", "config_json", "retry_json", "tool_policy", "knowledge_scopes")) {
            if (result.containsKey(key)) result.put(key, parse(result.get(key)));
        }
        return result;
    }

    private Object parse(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        String serializedValue = String.valueOf(value).trim();
        if (!looksLikeJsonContainer(serializedValue)) {
            return value;
        }
        try {
            return mapper.readValue(serializedValue, Object.class);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Agent 平台 JSON 字段解析失败，将保留原始值", exception);
            return value;
        }
    }

    private static boolean looksLikeJsonContainer(String value) {
        return (value.startsWith("{") && value.endsWith("}"))
                || (value.startsWith("[") && value.endsWith("]"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        Object parsed = parse(value);
        return parsed instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (JsonProcessingException e) { throw new BizException("JSON 配置无法序列化：" + e.getMessage()); }
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private static void requireCode(String value, String label) {
        if (!CODE.matcher(value).matches()) throw new BizException(label + "必须由大写字母、数字、下划线组成，并以字母开头");
    }

    private static String normalizeCode(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String value(String value) { return value == null ? "" : value.trim(); }
    private static String defaultValue(String value, String fallback) { return blank(value) ? fallback : value.trim(); }
    private static int number(Object value) { return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value)); }
    private static Map<String, Object> optionalMap(Map<String, Object> value) { return value == null ? Map.of() : value; }
    private static int bounded(Integer value, int fallback, int min, int max, String field) {
        int actual = value == null ? fallback : value;
        if (actual < min || actual > max) throw new BizException(field + " 必须在 " + min + " 到 " + max + " 之间");
        return actual;
    }

    public record AgentDraft(
            String code,
            String name,
            String description,
            String systemPrompt,
            String modelPolicy,
            Map<String, Object> inputSchema,
            Map<String, Object> outputSchema,
            Map<String, Object> toolPolicy,
            List<String> knowledgeScopes,
            Integer maxModelCalls,
            Integer timeoutSeconds) {}

    public record WorkflowDraft(
            String code,
            String name,
            String description,
            Map<String, Object> stateSchema,
            Map<String, Object> inputSchema,
            Map<String, Object> outputSchema,
            Map<String, Object> budget,
            List<NodeDraft> nodes,
            List<EdgeDraft> edges) {}

    public record NodeDraft(
            String code,
            String type,
            String name,
            Map<String, Object> config,
            Map<String, Object> retry,
            Integer timeoutSeconds) {}

    public record EdgeDraft(String from, String to, String condition, Integer priority) {}
}
