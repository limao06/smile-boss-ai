# 18. Chat2DB：Java 数据库客户端中的 AI SQL 产品化

## 项目卡片

- GitHub：[codephiliax/Chat2DB](https://github.com/codephiliax/Chat2DB)
- 本地源码：`research/source-code/agent-text2sql/18-chat2db`
- 锁定版本：`5ee1e990e73f`
- 定位：Java 17/TypeScript 的多数据库客户端，集成自然语言生成 SQL、解释、优化和执行。
- 一句话判断：Chat2DB 不是最先进的 Agentic Text-to-SQL 论文实现，但与 SmileBoss 技术栈最接近，最适合研究数据库方言插件、连接管理、编辑器交互和 AI 功能如何进入真实产品。
- 重要性：P0/P1。

## STAR

### S — Situation

Text-to-SQL 最终要嵌入数据库产品：用户选择连接、数据库、Schema，查看生成 SQL，手工修改、执行和查看结果。多数据库在 quoting、函数、分页和 metadata API 上各不相同。

### T — Task

提供连接管理、metadata introspection、方言/驱动插件、SQL 编辑执行和结果展示，并在上下文明确时调用 AI 生成、解释或优化 SQL。

### A — Action

后端按数据源类型连接数据库并读取 catalog/schema/table/column metadata；方言/插件层封装不同数据库能力。前端编辑器将自然语言、当前数据源和 Schema 上下文提交 AI 服务，得到 SQL 后先展示，用户可修改再执行。结果以表格呈现，AI 还可解释/优化 SQL。项目包含连接凭据加密等产品配置。

### R — Result

产品闭环和 Java 工程结构对 SmileBoss 很实用，多方言扩展思路清晰。局限是它偏数据库客户端，生成算法不是 CHESS/WrenAI 那种深度语义与多 Agent 流程；允许用户任意执行 SQL 的客户端模型也不适合直接开放给普通招聘用户。

## 完整执行流

1. 管理员创建加密数据源连接。
2. 后端读取 catalog/schema/table/column，按方言统一模型。
3. 用户选择数据库范围并输入自然语言。
4. 服务构建当前 Schema 上下文，调用模型生成 SQL。
5. UI 展示 SQL、解释和警告，允许编辑。
6. 用户明确执行后，后端用所选连接运行并返回分页结果。
7. 记录历史、错误和执行耗时；可再请求优化/解释。

## 难点

| 难点 | 级别 | 项目价值 | SmileBoss 差异 |
|---|---|---|---|
| 多方言 | P1 | 插件化 DB 支持 | 初期只支持 MySQL，减少攻击面 |
| 上下文选择 | P1 | 当前连接/Schema | 再加语义层和权限裁剪 |
| 凭据 | P0 | 加密配置 | Agent 永远拿不到明文凭据 |
| 人机协作 | P1 | 生成后编辑执行 | 普通用户只运行安全模板；管理员才看 SQL |
| SQL 产品体验 | P2 | 编辑器/结果/历史 | 管理端复用类似交互 |

## SmileBoss 借鉴

Java 包结构、方言接口、连接元数据和“生成—预览—确认—执行”体验值得参考。SmileBoss 面向业务用户时应更严格：仅 SELECT、受治理语义对象、固定最大时间范围、LIMIT、结果脱敏。不能因为 Chat2DB 能执行 DDL/DML 就把同等能力暴露给招聘 Agent。

## 评分

架构学习 4/5；生产成熟度 4/5；招聘相关性 3/5；技术栈接入成本 5/5；Java 产品集成的重要参考。

