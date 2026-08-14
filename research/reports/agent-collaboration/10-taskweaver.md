# 10. TaskWeaver：Planner + Code Interpreter 的数据分析 Agent

## 项目卡片

- GitHub：[microsoft/TaskWeaver](https://github.com/microsoft/TaskWeaver)
- 本地源码：`research/source-code/agent-text2sql/10-taskweaver`
- 锁定版本：`d44ddef23f90`
- 定位：代码优先的数据分析 Agent，包含 Planner、Code Interpreter、plugins、共享内存数据和经验。
- 状态说明：仓库在 2026-03 已归档；适合研究，不建议作为新生产基座。
- 一句话判断：最大价值是让数据分析通过“计划—生成代码—沙箱执行—观察错误—修复”闭环完成，并在内存中复用表格对象，避免数据反复塞回 LLM。
- 重要性：安全 P0，方法 P1。

## STAR

### S — Situation

分析用户活跃度、招聘漏斗和面试指标需要 SQL、DataFrame 和图表。模型直接口算不可靠，把完整数据回传模型又昂贵且泄露隐私；代码执行本身还可能破坏系统。

### T — Task

Planner 拆解分析任务，Code Interpreter 生成并在受限环境执行代码/插件；结果、错误和内存数据成为下一轮观察，有限次修复后输出表格、图和解释。

### A — Action

Planner 读取请求与会话状态，制定计划并调用 CodeInterpreter。后者生成代码，可使用注册 plugin，将 DataFrame 等对象保留在进程内并只把摘要传给 LLM。执行器捕获 stdout、结果和异常；错误反馈给模型修改代码。经验/示例检索帮助选择已有解法，tracing/eval 记录运行。

### R — Result

比纯文本 Agent 更适合真实数据分析，数据无需每轮序列化进提示词。核心风险是任意代码和 SQL 权限、资源耗尽、隐私输出和提示注入；仓库归档又增加维护与漏洞修复风险。

## 完整执行流

1. 用户问题进入 Planner，识别需要的数据和交付物。
2. 检索可用 plugin/经验，产生分步 plan。
3. Code Interpreter 生成代码，只访问允许的数据句柄。
4. 隔离执行，限制 CPU、内存、时间、网络和文件系统。
5. 返回结果/异常；失败时模型解释并有限次修复。
6. 验证输出行数、隐私阈值和图表字段。
7. 生成自然语言结论，附 SQL/代码/数据快照版本。

## 难点

| 难点 | 级别 | 做法 | SmileBoss 要求 |
|---|---|---|---|
| 任意代码 | P0 | sandbox | 默认禁网、只读数据、容器资源限制 |
| SQL 越权 | P0 | plugins/连接 | 只暴露受治理查询 API，不给生产凭据 |
| 修复循环 | P1/P2 | error observation | 最大重试、同错重复即停止 |
| 数据回传 | P0 | in-memory data | 只给聚合/脱敏摘要，最小样本阈值 |
| 项目归档 | P1 | 无持续维护 | 借鉴模式，自研受控 Analytics Agent |

## SmileBoss 借鉴

“账号活跃分析”可以采用同样闭环，但优先让 Text-to-SQL 生成只读查询，经 AST/权限验证后在只读副本执行；只有受控统计和画图才进入代码沙箱。禁止分析私人聊天内容、推断敏感属性或输出小样本个人排名。

## 评分

架构学习 5/5；生产成熟度 2/5（归档因素）；招聘相关性 4/5；接入成本 1/5；仅借鉴 code-first 分析闭环。

