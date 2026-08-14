# 17. Haystack：显式组件流水线与系统化 RAG 评估

## 项目卡片

- GitHub：[deepset-ai/haystack](https://github.com/deepset-ai/haystack)
- 本地源码：`research/source-code/rag/17-haystack`
- 锁定版本：`ba92ec9de3be`
- 定位：成熟的模块化 AI pipeline 框架，拥有丰富 retriever、ranker、router 和 evaluator。
- 一句话判断：Haystack 的核心价值不是某个最佳算法，而是把每一步做成有类型输入输出的可替换组件，并给出足够完整的评估工具箱。
- 重要性：P1。

## STAR

### S — Situation

RAG 优化需要不断替换切块、embedding、检索、融合、重排和提示；如果所有逻辑写在一个服务方法里，无法单测、AB 或定位回归。只评价最终答案又不知道问题源于召回还是生成。

### T — Task

用显式 Pipeline 连接组件，支持 BM25、embedding、多查询、多检索器、过滤、sentence window、auto-merge 和多种 ranker；分别评估文档排名、上下文相关性、回答正确性和忠实度。

### A — Action

每个 component 声明输入输出，Pipeline 检查并连接端口。查询可经过 router、query expander、多个 retriever、joiner、ranker、prompt builder 和 generator。Sentence-window retrieval 用小句命中后扩展窗口，AutoMergingRetriever 按层级块合并。评估组件覆盖 Recall、MRR、nDCG、context relevance、faithfulness 等，并能把各阶段结果持久化比较。

### R — Result

实验和生产逻辑更清晰，可分阶段回答“没召回、排序错、还是模型乱说”。缺点是组件选择繁多，若没有固定基线容易陷入框架调参；Python 运行时也不必直接嵌入 Java 主服务。

## 完整执行流

1. 索引 Pipeline：converter -> cleaner -> splitter -> embedder -> writer。
2. 查询 Pipeline：router/filter -> query embedder/expander。
3. 并行 BM25/embedding retriever，joiner 融合。
4. sentence window 或 parent auto-merge 补上下文。
5. ranker 精排，prompt builder 拼装证据。
6. generator 输出答案，citation 绑定来源。
7. evaluator 分别对 retrieval 和 generation 评分。

## 难点

| 难点 | 级别 | 做法 | SmileBoss 实施 |
|---|---|---|---|
| 组件契约 | P1 | typed component I/O | Java 为每节点定义 request/result DTO |
| 多检索器融合 | P1 | joiners/rankers | 保存每路分数和融合贡献 |
| 层次块 | P1 | window/auto-merge | 对经历/职责使用父子层次 |
| 分层评估 | P1 | MRR/nDCG/faithfulness | 建 4 套集：解析、召回、精排、答案 |
| 复杂选择 | P2 | 大量组件 | 先固定一条基线，数据证明后再增加组件 |

## SmileBoss 借鉴

不必引入 Haystack runtime，但应复制其组件化和评估思想。每次实验记录 parser、chunker、embedding、index、fusion、reranker、prompt 和 model 的版本；召回金标与最终推荐金标分开。任何优化若 Recall@50 降低，即使最终少量样本看起来更好，也不能轻易上线。

## 评分

架构学习 5/5；生产成熟度 5/5；招聘相关性 3/5；接入成本 3/5；作为组件设计和评估方法的首要参考。

