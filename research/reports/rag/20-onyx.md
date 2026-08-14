# 20. Onyx：企业连接器、ACL 前置过滤与混合 Agentic RAG

## 项目卡片

- GitHub：[onyx-dot-app/onyx](https://github.com/onyx-dot-app/onyx)
- 本地源码：`research/source-code/rag/20-onyx`
- 锁定版本：`26ff6e8835d3`
- 定位：企业搜索与 Agentic RAG，包含大量连接器、ACL、文档集、混合搜索和复杂基础设施。
- 一句话判断：Onyx 对 SmileBoss 最重要的不是 50+ 连接器，而是“权限必须在检索前进入查询，并在返回前再次审查”。
- 重要性：P0。

## STAR

### S — Situation

企业知识来自多系统，每个文档有不同访问权。若先全库召回再让 LLM过滤，敏感内容已经进入模型上下文和日志；连接器同步时权限变化、删除和版本更新也会造成陈旧泄露。

### T — Task

同步内容及 ACL，允许用户按文档集、来源、日期和标签查询；在检索前施加权限过滤，融合语义/关键词结果、合并相邻块，并在输出前再次做 post-query censorship。

### A — Action

连接器抓取文档和权限信息，索引层保存 source、tenant、docset、ACL、日期、标签等 metadata。请求先解析用户身份和可访问 scope，再向 Vespa/OpenSearch 等后端提交带过滤条件的混合查询。结果合并相邻块以恢复上下文，进入 Agent/LLM；返回前再次检查引用和文档可见性。Redis、对象存储和搜索基础设施支撑任务、缓存和内容。

### R — Result

显著降低“模型看到后再删”的假安全，适合学习权限随文档同步的生命周期。代价是基础设施重、连接器和 ACL 模型复杂；若上游权限同步滞后，仍可能泄露。

## 完整执行流

1. 连接器以服务账户拉取文档、增量游标和 ACL。
2. 规范化文档，切块并保存 owner/source/docset/tenant/ACL/version。
3. 写入混合搜索索引；权限变化触发更新或删除。
4. 查询时从认证上下文解析用户、组和租户。
5. 构造 ACL + docset + source/date/tag 的前置过滤。
6. 执行 semantic/keyword 混合检索和排序。
7. 合并相邻块，控制上下文并生成答案。
8. post-query censorship 再验证每条结果/引用当前仍可见。
9. 记录审计日志；撤权后使缓存和派生答案失效。

## 难点

| 难点 | 级别 | 做法 | 招聘对应 |
|---|---|---|---|
| ACL 前置 | P0 | search filter | tenant + recruiter assignment + consent + process scope |
| 权限同步 | P0 | connector sync | 授权变更事件、短缓存 TTL、即时撤权 |
| 相邻块恢复 | P1 | chunk merge | 还原完整经历/面试回答，同时限制跨权限边界 |
| 二次审查 | P0 | censorship | 返回前按最新权限重查 source IDs |
| 基础设施 | P2 | 多组件 | SmileBoss 复用模式，不复制整套部署 |

## SmileBoss 借鉴

Qdrant 查询必须携带 `tenant_id`、`candidate_visibility`、`recruitment_process_id` 和 `document_status` filter。生成上下文中的每个证据 ID 返回前再查 MySQL 当前授权。缓存 key 包含权限版本；候选撤回授权时递增版本并清空相关缓存。切勿把“请不要泄露其他候选人”写在 prompt 中当作访问控制。

## 评分

架构学习 5/5；生产成熟度 5/5；招聘相关性 4/5；接入成本 1/5；权限与企业检索的首要参考。

