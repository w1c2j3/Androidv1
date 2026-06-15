# 视流 AI 重构计划

更新日期：2026-06-13

目标版本：v2 重构版

本文用于固定当前真实状态、问题根因、重构路线和验收标准。不要在本文写入真实 App Secret、LLM Key、Admin Token 或临时公网隧道地址。

## 1. 当前状态

### 1.1 项目定位

当前项目是一个课程答辩和原型验证型系统，核心链路是：

1. Android App 或飞书机器人接收图片/文本输入。
2. Java Spring Boot 后端接收请求、保存状态、管理任务和 Agent run。
3. 本地 OCR 服务识别图片。
4. 后端把 OCR 结果整理为摘要、任务、链接、日报素材。
5. Android 和飞书卡片展示结果，并允许保存任务、生成日报素材。

当前项目不是一个成熟产品。它已经有端到端雏形，但在 OCR 后结构化抽取、飞书卡片回调、Android 前端信息架构、任务/论文入口等方面需要系统重构。

### 1.2 后端当前状态

后端位置：`backend/`

核心技术：

- Java 17
- Spring Boot 3.3.5
- Spring MVC
- Spring Data JPA / Hibernate
- H2 文件数据库，默认 `jdbc:h2:file:./data/shiliu-v1`
- PostgreSQL 驱动已引入，但当前默认不是 PostgreSQL
- Jackson JSON
- Spring `RestClient`
- `ThreadPoolTaskExecutor`
- 自定义 `AdminTokenInterceptor`

当前已有接口：

- `GET /api/v1/health`
- `GET /api/v1/setup/readiness`
- `GET /api/v1/setup/queues`
- `GET /api/v1/workbench/overview`
- `POST /api/v1/vision/upload`
- `GET /api/v1/vision/results/{traceId}`
- `GET /api/v1/vision/traces`
- `POST /api/v1/tasks/from-trace/{traceId}`
- `GET /api/v1/tasks`
- `POST /api/v1/tasks`
- `PATCH /api/v1/tasks/{taskId}/status`
- `POST /api/v1/agent/runs`
- `GET /api/v1/agent/runs`
- `GET /api/v1/agent/runs/{runId}`
- `POST /api/v1/agent/runs/{runId}/tasks`
- `POST /feishu/events/{botId}`
- `POST /feishu/card-callback/{botId}`

当前已确认：

- 任务接口后端是通的，`/api/v1/tasks` 能返回历史任务。
- 论文检索后端仍然存在，`/paper` 命令能走 `Research Agent`，能返回论文候选。
- LLM 配置已存在，`SHILIU_LLM_API_BASE_URL` 指向 OpenAI-compatible 服务，`SHILIU_LLM_MODEL` 可使用 `gpt-4o-mini` 一类模型。
- LLM 接口本身已经验证可返回内容。

当前主要问题：

- OCR 完成后的结构化抽取仍然走 `RuleBasedExtractService`，不是 LLM-first。
- `RuleBasedExtractService` 在 OCR 原文为空时固定返回 `confidence=0.4`、`未识别到明确事项`、`图片中没有明显任务或链接`。
- 当前卡片里的“图片类型：结构化结果｜置信度 40%”不是模型判断，而是代码固定兜底。
- 飞书卡片按钮点击“保存任务”“生成日报素材”会出现 `code:200672`，当前高度怀疑是飞书卡片回调响应格式或返回卡片 JSON 不符合飞书真实协议。
- 后端本地 MockMvc 覆盖了 card callback，但没有覆盖飞书真实客户端协议兼容性。

### 1.3 OCR 当前状态

OCR 服务位置：`ocr-service/`

当前 OCR 是本地可运行模型服务，不依赖云 OCR。

当前技术：

- Python FastAPI
- Uvicorn
- PaddleOCR
- PaddlePaddle
- PP-OCRv5
- 默认模型配置：
  - `OCR_LANG=ch`
  - `OCR_MODEL_PROFILE=mobile`
  - `PP-OCRv5_mobile_det`
  - `PP-OCRv5_mobile_rec`

当前运行方式：

```bash
./scripts/run-ocr-service.sh
```

健康检查：

```bash
curl http://127.0.0.1:9000/health
```

当前 OCR 服务设计：

- 后端通过 `HttpOcrProvider` 以 multipart 方式调用 OCR 服务。
- OCR 服务返回统一 `OcrResult`：
  - `traceId`
  - `imageType`
  - `width`
  - `height`
  - `plainText`
  - `blocks`
  - `quality`

当前主要问题：

- OCR 本身可以本地跑，但后端没有充分展示 OCR 置信度。
- 当前飞书卡片展示的置信度是摘要置信度，不是 OCR 模型平均置信度。
- OCR 引擎还没有做多后端抽象，无法像 RapidOCR 那样切换 ONNX Runtime、OpenVINO、PaddlePaddle 等后端。
- 大图 OCR 可能较慢，需要前端和飞书卡片明确展示阶段和等待状态。

### 1.4 Android 当前状态

Android 位置：`android/`

当前技术：

- 原生 Android Java
- 单 `MainActivity`
- 程序化 UI
- AppCompat
- Material Components
- `HttpURLConnection`
- `BuildConfig` 注入后端 URL、Admin Token、飞书 bot 信息

当前已实现：

- 后端体检
- 图片选择
- 图片上传
- OCR 结果轮询
- 任务创建
- 任务列表读取入口
- 飞书机器人状态检查
- Agent run 创建
- Digest 日报/群总结

当前主要问题：

- 前端是调试面板，不是清晰产品 UI。
- 任务列表后端可用，但 App 里容易因为旧 APK、旧 tunnel、旧 token、错误展示粗糙而表现为“读取失败”。
- 论文检索后端存在，但 Android 没有独立“论文收集”页面，功能藏在 Agent 命令输入里，用户会认为功能消失。
- OCR 结果、任务候选、日报素材、保存动作没有形成清晰工作流。
- 页面缺少“当前后端 / 当前数据库 / 任务数量 / LLM 状态 / OCR 状态 / 飞书回调状态”的明确可见信息。

### 1.5 飞书机器人当前状态

当前已有：

- 飞书事件订阅入口。
- 文本消息处理。
- 图片消息下载。
- OCR 后发送结果卡片。
- 交互卡片按钮回调代码。

当前主要问题：

- 飞书卡片 action 回调没有独立日志表，线上报错时只能从客户端看到 `code:200672`。
- 卡片回调响应格式没有按真实飞书协议做适配层。
- 卡片构造、action 路由、业务保存逻辑耦合在一起。
- 当前更像“能跑的飞书接入”，还不是 LangBot 那种成熟 channel/adapter/plugin 架构。

## 2. 根因判断

### 2.1 置信度 40% 的根因

根因不是 GPT 模型质量，也不是 OCR 模型本身。

真实原因：

- 当前 `VisionPipelineService` 调用的是 `ExtractService`。
- 当前唯一主实现是 `RuleBasedExtractService`。
- `RuleBasedExtractService` 只按规则读 `plainText`，没有把 OCR JSON 发给 GPT。
- 当 OCR 原文为空或规则没识别到时，会固定生成低置信度兜底摘要。

结论：

必须将 `ExtractService` 改成 LLM-first、rule-fallback。

### 2.2 飞书卡片按钮出错的根因

根因尚需用真实飞书回调日志最终确认，但当前最可疑点是：

- 回调响应体格式不符合飞书真实交互卡片协议。
- 返回的卡片 JSON 结构或 toast/card 包装格式不被飞书客户端接受。
- 本地 MockMvc 测试只验证了后端 JSON 形状，没有验证飞书协议兼容性。
- 没有 action log，无法回放飞书真实请求体和后端响应体。

结论：

必须新增 `FeishuCardActionLog`，并把卡片回调做成可观测、可回放、可降级的 adapter。

### 2.3 App 任务列表/论文功能“消失”的根因

后端任务和论文接口仍在。

根因更偏前端信息架构：

- Android 没有独立论文页面。
- 任务页只是 raw JSON 展示，不是任务列表 UI。
- 错误展示不够明确，用户看不到是 URL、token、HTTP、JSON 解析还是后端错误。
- Quick Tunnel 地址变化后，旧 APK 会连到旧地址，导致看起来像“之前的数据没有了”。

结论：

Android 前端需要重做，不应继续堆调试按钮。

## 3. 参考项目

### 3.1 RapidOCR

参考链接：https://github.com/RapidAI/RapidOCR

参考点：

- OCR 工程化部署。
- 多后端推理支持。
- ONNX Runtime / OpenVINO / MNN / PaddlePaddle 等后端可选。
- 更轻量的推理服务。
- 更明确的模型 profile 和运行环境隔离。

本项目吸收方式：

- 不立即替换 PaddleOCR。
- 先抽象 OCR engine/provider。
- 当前保留 PaddleOCR 本地模型服务作为默认实现。
- 后续增加 RapidOCR engine 作为可选实现。
- 后端永远只依赖统一 `OcrResult`，不绑定具体 OCR 引擎。

### 3.2 LangBot

参考链接：https://github.com/langbot-app/LangBot

参考点：

- 多平台机器人 channel 抽象。
- 适配器架构。
- 插件化命令处理。
- 知识库和 Agent 编排。
- 飞书、钉钉、企业微信等多渠道统一入口。

本项目吸收方式：

- 不直接迁移到 LangBot。
- 将飞书机器人部分拆出 channel/adapter/action router。
- 把 OCR、任务、日报、Agent 做成后端 command/action。
- 飞书只作为一种输入输出 channel。
- Android 也是另一个 channel。

## 4. 目标架构

### 4.1 总体目标

重构后的核心链路必须变成：

```text
图片/文本输入
  -> Channel Adapter(Android / Feishu)
  -> Command Router
  -> OCR Provider(local PaddleOCR / future RapidOCR)
  -> LLM Structured Extractor
  -> Persisted Trace / Tasks / Report Materials
  -> UI Renderer(Android / Feishu Card)
```

### 4.2 OCR 目标架构

新增抽象：

```text
OcrEngine
  - PaddleOcrEngine
  - RapidOcrEngine(future)
  - StaticOcrEngine(test)

OcrProvider
  - HttpOcrProvider
  - LocalOcrProvider(future if Java direct bridge is needed)
```

OCR 统一结果字段：

- `engine`
- `engineVersion`
- `modelProfile`
- `lang`
- `latencyMs`
- `plainText`
- `blocks`
- `averageConfidence`
- `minConfidence`
- `width`
- `height`
- `quality`

### 4.3 结构化抽取目标架构

新增：

- `LlmStructuredExtractService`
- `StructuredExtractPromptBuilder`
- `StructuredExtractJsonParser`
- `ExtractFallbackService`
- `ExtractQualityScorer`

抽取策略：

1. OCR 原文和 blocks 不为空时，优先调用 LLM。
2. LLM 必须返回严格 JSON。
3. JSON 解析失败时尝试修复一次。
4. 修复失败则降级规则抽取。
5. 结果必须记录 `extractMode`。

目标 `extractMode`：

- `llm`
- `llm_repaired`
- `rule_fallback`
- `failed`

LLM 输入应包含：

- OCR `plainText`
- OCR `blocks`
- OCR 置信度
- 来源 `source`
- 场景提示 `sceneHint`
- traceId
- 用户上下文
- 输出语言要求

LLM 输出 JSON 示例：

```json
{
  "scene": "chat_screenshot",
  "summary": {
    "title": "项目任务讨论",
    "bullets": ["确认后端接口", "修复飞书卡片回调"],
    "confidence": 0.91
  },
  "tasks": [
    {
      "title": "修复飞书卡片回调响应格式",
      "owner": "未指定",
      "dueText": "",
      "priority": "high",
      "confidence": 0.88,
      "evidence": "点击保存任务时报错 code:200672"
    }
  ],
  "links": [],
  "dailyReportMaterials": ["完成 OCR 到 LLM 结构化抽取方案设计"],
  "riskFlags": [
    {
      "type": "integration_risk",
      "message": "飞书卡片回调协议需要真实客户端验证"
    }
  ]
}
```

### 4.4 飞书目标架构

新增分层：

- `FeishuEventAdapter`
- `FeishuMessageSender`
- `FeishuCardRenderer`
- `FeishuActionRouter`
- `FeishuActionResponseAdapter`
- `FeishuCardActionLogService`

飞书 action 不直接散落在 service 中，统一定义：

- `trace.open_task_confirm`
- `trace.save_tasks`
- `trace.create_report`
- `trace.reextract_llm`
- `agent.save_tasks`
- `report.show`
- `ignore`

每次按钮点击都必须落日志：

- `id`
- `botId`
- `traceId`
- `action`
- `requestJson`
- `responseJson`
- `status`
- `errorCode`
- `errorMessage`
- `createdAt`

### 4.5 Android 目标架构

短期继续保持原生 Java，满足 Android 课程和当前工程成本。

但 UI 信息架构必须重做为 5 个主 Tab：

1. 首页
2. OCR
3. 任务
4. 论文
5. 飞书/设置

不再把所有能力藏在 raw JSON 和按钮堆里。

## 5. 实施计划

### Phase 0：冻结当前状态

目标：

- 保留当前可运行能力。
- 明确哪些是旧实现问题。
- 不再继续在旧 UI 上堆功能。

任务：

- 确认当前后端、OCR、Android 打包链路。
- 记录当前接口可用性。
- 保留当前 H2 数据。
- 保留当前飞书机器人配置。
- 禁止把 secret 写入仓库。

验收：

- `GET /api/v1/health` 正常。
- `GET /api/v1/tasks` 正常。
- `/paper` Agent 正常。
- OCR `/health` 正常。
- LLM chat completions 正常。

### Phase 1：LLM 结构化抽取

目标：

让 OCR 结果真正进入 GPT 家族模型，由模型输出结构化 JSON。

任务：

1. 扩展 `LlmChatService`：
   - 增加 `chatJson(...)`。
   - 增加模型健康检查。
   - 增加超时和错误返回。
2. 新增 `LlmStructuredExtractService`。
3. 保留 `RuleBasedExtractService` 作为 fallback。
4. 修改 `VisionPipelineService.extract(...)` 使用 LLM-first。
5. `ExtractResult` 增加：
   - `extractMode`
   - `llmModel`
   - `ocrConfidence`
   - `extractConfidence`
   - `rawModelJson`
   - `extractError`
6. 后端测试覆盖：
   - LLM 成功。
   - LLM JSON 损坏后修复。
   - LLM 失败后规则 fallback。
   - OCR 原文为空时明确说明，不伪装成模型判断。

验收：

- 飞书图片结果不再固定 40%。
- 卡片能显示 `抽取方式：LLM`。
- 摘要不再固定为“图片中没有明显任务或链接”。
- 任务候选来自模型 JSON，并包含 evidence。

### Phase 2：OCR 本地模型工程化

目标：

明确 OCR 模型本地运行，并为 RapidOCR 后续接入留出扩展点。

任务：

1. OCR `/health` 返回：
   - `engine`
   - `modelProfile`
   - `lang`
   - `localModel=true`
2. OCR `/ocr` 返回：
   - `engine`
   - `latencyMs`
   - `averageConfidence`
3. 后端 `OcrResult` DTO 对齐这些字段。
4. 新增 OCR engine 配置：
   - `OCR_ENGINE=paddleocr`
   - `OCR_MODEL_PROFILE=mobile|server`
5. 预留：
   - `OCR_ENGINE=rapidocr`
6. 文档说明：
   - 当前默认本地 PaddleOCR。
   - RapidOCR 是后续轻量化方向。

验收：

- 不联网也能跑 OCR 模型。
- `/health` 能说明当前 OCR 引擎。
- 前端显示 OCR 置信度，而不是把摘要置信度当 OCR 置信度。

### Phase 3：飞书卡片回调修复

目标：

解决点击“保存任务”“生成日报素材”报 `code:200672`。

任务：

1. 对照飞书交互卡片回调官方协议，重写 callback response adapter。
2. 后端不直接返回随意 Map，由 `FeishuActionResponseAdapter` 统一生成。
3. 简化按钮响应：
   - 点击后先返回最小合法 toast。
   - 后台异步更新卡片或发送新卡片。
4. 新增 `card_action_logs`。
5. 每次 action 记录请求、响应、错误。
6. 增加真实 payload 回放测试。

验收：

- 飞书点击保存任务不报错。
- 飞书点击生成日报不报错。
- 后端能查到 action log。
- action 失败时飞书卡片提示可读错误。

### Phase 4：Android 前端重做

目标：

从调试工具重构为可演示、可使用的移动工作台。

页面设计：

1. 首页
   - 当前后端地址
   - 后端健康
   - OCR 健康
   - LLM 健康
   - 飞书健康
   - 今日任务数
   - 今日 OCR 数
   - 最近错误
2. OCR
   - 选择图片
   - 上传进度
   - OCR 阶段
   - OCR 原文
   - LLM 结构化结果
   - 任务候选
   - 保存任务
   - 生成日报
   - 重新模型抽取
3. 任务
   - 列表不是 raw JSON。
   - 支持状态筛选。
   - 支持来源筛选。
   - 点击任务展示 evidence。
4. 论文
   - 输入研究主题。
   - 调用 `/paper`。
   - 展示论文标题、年份、来源、摘要、链接。
   - 保存阅读任务。
5. 飞书/设置
   - 事件订阅 URL。
   - 卡片回调 URL。
   - 最近飞书事件。
   - 最近卡片 action。
   - 最近错误。

验收：

- 用户无需懂 API 也能操作。
- 任务列表能正常展示已有任务。
- 论文检索有独立入口。
- OCR 到日报、任务保存形成闭环。
- 所有错误显示接口、状态码、错误体。

### Phase 5：数据库和信息管理升级

目标：

把信息持久化从“能存”升级为“能追踪、能审计、能解释”。

需要新增或扩展：

1. `vision_traces`
   - `ocrEngine`
   - `ocrConfidence`
   - `extractMode`
   - `llmModel`
   - `extractConfidence`
   - `extractError`
2. `task_items`
   - `sourceType`
   - `sourceId`
   - `evidenceText`
   - `confirmedByUser`
3. 新表 `report_materials`
   - `id`
   - `traceId`
   - `content`
   - `type`
   - `createdAt`
4. 新表 `card_action_logs`
   - 记录飞书按钮点击全过程。

验收：

- 每条任务能追溯来源。
- 每条日报素材能追溯 trace。
- 每次飞书按钮失败能查日志。
- App 能显示“这条任务来自哪张图/哪次 Agent run”。

## 6. 验收成果

### 6.1 技术验收

后端：

- `./mvnw test` 通过。
- LLM 结构化抽取测试通过。
- 飞书 action callback 测试通过。
- 任务、日报、论文接口测试通过。

OCR：

- 本地 OCR `/health` 正常。
- 本地 OCR 图片识别正常。
- 返回 OCR 引擎、模型、平均置信度。

Android：

- `:app:assembleDebug` 成功。
- APK 首页显示当前后端、OCR、LLM、飞书状态。
- 任务列表不是 raw JSON。
- 论文检索独立可用。

飞书：

- 发送图片后结果卡正常。
- 保存任务按钮正常。
- 生成日报素材按钮正常。
- 失败时后端有 action log。

### 6.2 产品验收

必须能现场演示：

1. 手机上传截图。
2. 后端 OCR。
3. GPT 结构化抽取。
4. App 展示任务候选和日报素材。
5. 保存任务。
6. 飞书发送图片。
7. 飞书卡片点击保存任务。
8. 飞书卡片生成日报。
9. 论文检索。
10. 任务列表查看历史任务。

### 6.3 答辩验收

Java 企业应用开发：

- 能说明 Spring Boot 分层架构。
- 能说明 JPA 持久化。
- 能说明异步 OCR 队列。
- 能说明外部系统集成：OCR、LLM、飞书、论文检索。
- 能说明错误处理和日志审计。

Android 开发：

- 能说明原生 Android UI。
- 能说明图片选择和 multipart 上传。
- 能说明 Handler/Executor 线程模型。
- 能说明轮询 traceId/runId。
- 能展示真实任务列表、OCR 结果和论文检索。

## 7. 优先级

P0：

1. LLM-first 结构化抽取。
2. 飞书卡片按钮 `200672` 修复。
3. Android 任务列表和论文页面恢复。

P1：

1. OCR 本地模型健康和置信度展示。
2. card action log。
3. report materials 持久化。

P2：

1. RapidOCR engine 接入。
2. LangBot 风格插件化 channel。
3. PostgreSQL 生产化。
4. 用户权限体系。

## 8. 不做事项

当前阶段不做：

- 不把真实 LLM Key 写入仓库。
- 不把临时 Cloudflare URL 当长期配置。
- 不直接重写为 Flutter 或 React Native。
- 不在 Android 端内置 OCR 大模型。
- 不承诺飞书触发 Codex 自动改代码，除非先实现权限确认、任务队列、diff 审核和回滚机制。
