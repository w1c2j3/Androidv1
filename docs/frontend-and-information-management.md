# 前端与信息管理说明

本文回答两个问题：

1. 前端分别怎么做，当前有哪些端，PC 端缺什么。
2. 信息管理怎么做，数据如何进入、保存、流转和展示。

## 当前结论

当前项目已经用了 Java Web 的核心能力，但主要用于后端 REST API 和飞书回调，不是一个完整的 PC Web 前端。

现有前端形态是：

| 前端入口 | 当前状态 | 作用 |
| --- | --- | --- |
| Android App | 已实现 | 手机端命令中心、OCR 上传、任务、飞书状态、AgentRun 操作 |
| 飞书机器人 | 已实现 | 群聊/私聊入口，接收文本和图片，返回文本或互动卡片 |
| PC Web 工作台 | 未实现 | 目前只有 API 和静态原型，没有可运行的 PC 工作台页面 |
| 静态原型 HTML | 有，但不是产品前端 | `shiliu_ai_detailed_interaction_prototype.html` 只用于说明交互，不接真实 API |

因此，对外表达要准确：本项目当前是 Android-first + Feishu bot + Java Web backend。PC 端应该补一个 Web 工作台，而不是说已经有完整 PC 前端。

## 前端是如何做的

### 1. Android 前端

Android 是当前第一客户端，位置在：

```text
android/app/src/main/java/com/shiliuai/app/MainActivity.java
```

技术栈：

- 原生 Android Java
- Android Gradle Plugin
- AppCompat
- Material Components
- `HttpURLConnection` 直接调用后端 HTTP API

Android 构建时把运行配置注入到 `BuildConfig`：

```text
SHILIU_DEFAULT_BACKEND_URL
SHILIU_DEFAULT_ADMIN_TOKEN
SHILIU_DEFAULT_PROJECT_PATH
SHILIU_DEFAULT_BOT_ID
SHILIU_DEFAULT_BOT_NAME
SHILIU_DEFAULT_BOT_VERIFICATION_TOKEN
```

这意味着短期演示 APK 自带后端地址、管理员令牌和飞书机器人配置。Cloudflare Quick Tunnel 变化后，需要重新打包 APK。

Android 当前负责：

- 后端健康检查：`GET /api/v1/health`
- 环境就绪检查：`GET /api/v1/setup/readiness`
- 队列检查：`GET /api/v1/setup/queues`
- 工作台概览：`GET /api/v1/workbench/overview`
- OCR 上传：`POST /api/v1/vision/upload`
- OCR 结果轮询：`GET /api/v1/vision/results/{traceId}`
- 保存 OCR 任务：`POST /api/v1/tasks/from-trace/{traceId}`
- 生成日报素材：读取 `dailyReportMaterials`、`summary`、`tasks`
- Digest/项目分析/论文等 AgentRun：`POST /api/v1/agent/runs`
- 保存 Agent 任务候选：`POST /api/v1/agent/runs/{runId}/tasks`
- 任务创建和任务列表：`POST /api/v1/tasks`、`GET /api/v1/tasks`
- 飞书机器人状态：`GET /api/v1/bots/{botId}/health`

Android 的设计特点是操作可视化：每次请求会显示阶段、进度、结果 JSON 或错误内容。它适合真机演示和现场排查。

### 2. 飞书前端

飞书机器人是第二个前端入口，位置在：

```text
backend/src/main/java/com/shiliuai/controller/FeishuEventController.java
backend/src/main/java/com/shiliuai/controller/FeishuCardCallbackController.java
backend/src/main/java/com/shiliuai/service/feishu/
backend/src/main/java/com/shiliuai/service/card/FeishuCardService.java
```

飞书不是传统页面前端，而是聊天界面 + 互动卡片。

飞书当前支持：

- URL verification：返回 `challenge`
- 文本 `/ping`：回复 `pong`
- 文本任务：例如“添加任务：整理日报”
- `/paper`：论文检索
- `/digest`：读取真实群消息或用户提供文本后总结
- `/plan`、`/bug`：只读项目扫描
- 图片消息：下载飞书图片，进入 OCR 和结构化整理
- 图片处理卡片：显示处理中、整理完成、保存任务、生成日报素材等按钮

图片整理链路是：

```text
Feishu 图片事件
  -> /feishu/events/{botId}
  -> 创建 vision trace
  -> 发送“处理中”卡片
  -> 下载飞书图片资源
  -> OCR Service
  -> RuleBasedExtractService 抽取摘要、任务、链接、日报素材
  -> 更新原卡片为结果卡
  -> 用户点击保存任务或生成日报素材
  -> /feishu/card-callback/{botId}
```

注意：当前代码主要是更新原“处理中”卡片为结果卡，不一定额外发送一条普通文本消息。所以用户如果没有注意卡片更新，会感觉“整理后没发消息”。产品上更稳的做法是：结果卡更新成功后，再额外发送一条简短文本总结。

### 3. PC Web 前端

当前没有真正 PC Web 前端。

已有的 Java Web 后端能提供 PC 页面所需 API，但仓库里没有：

- React/Vue/Vite/Next.js 前端工程
- Thymeleaf/JSP 模板页面
- Spring MVC 页面 Controller
- 静态资源构建和路由

目前只有：

```text
shiliu_ai_detailed_interaction_prototype.html
```

这个文件是静态交互原型，不接真实后端 API，不能算正式 PC 工作台。

如果要补 PC 端，建议做一个真正的 Web 工作台，优先页面如下：

| 页面 | 关键能力 |
| --- | --- |
| 总览 | health、readiness、队列、今日 OCR/任务概览 |
| OCR 工作台 | 上传图片、看 trace 进度、查看 OCR 原文、保存任务、生成日报素材 |
| 任务中心 | 列表、状态切换、来源筛选、trace/run 关联 |
| AgentRun | 输入命令和 contextText、查看 runs、保存任务候选 |
| 飞书配置 | 展示当前事件订阅 URL、卡片回调 URL、bot health |
| 记忆库 | 查看和新增长期记忆 |

技术上可以选两条路线：

1. Spring Boot 直接服务静态 HTML/JS。
   适合快速演示，依赖少，部署简单。
2. 新建 React/Vite 前端。
   适合更完整的 PC 工作台，交互和状态管理更好。

## Java Web 核心使用情况

后端位置：

```text
backend/
```

核心框架：

| 框架/组件 | 当前用途 |
| --- | --- |
| Spring Boot 3.3.5 | 后端应用入口、自动配置、嵌入式 Tomcat |
| Spring MVC / starter-web | REST Controller、飞书回调、JSON API |
| Jackson | JSON 序列化/反序列化 |
| Spring Validation | 请求参数和 DTO 校验 |
| Spring Data JPA | 数据库存取 |
| Hibernate | JPA 实现 |
| H2 | 本地默认文件数据库 |
| PostgreSQL Driver | Docker/部署时可切 PostgreSQL |
| RestClient | 调飞书 OpenAPI、LLM、OCR 健康检查、论文数据源 |
| TaskExecutor | OCR 和飞书事件后台任务队列 |

典型 Controller：

```text
HealthController
SetupController
WorkbenchController
VisionController
TaskController
MemoryController
AgentRunController
BotAdminController
FeishuEventController
FeishuCardCallbackController
```

这说明 Java Web 栈已经承担核心后端职责：鉴权、API 编排、任务队列、数据持久化、第三方回调。但它还没有承担 PC 页面渲染职责。

## 信息管理是如何做的

信息管理分成五类：配置、事件、识别结果、任务、长期记忆。

### 1. 配置信息

配置来自 `.env.local` 和环境变量，不提交真实密钥。

关键配置包括：

```text
SHILIU_ADMIN_TOKEN
SHILIU_MOBILE_BACKEND_URL
SHILIU_PUBLIC_BASE_URL
SHILIU_FEISHU_APP_ID
SHILIU_FEISHU_APP_SECRET
SHILIU_FEISHU_BOTS_0_BOT_ID
SHILIU_FEISHU_BOTS_0_VERIFICATION_TOKEN
SHILIU_LLM_API_BASE_URL
SHILIU_LLM_API_KEY
SHILIU_AGENT_ALLOWED_PROJECT_ROOTS
```

Android 的运行配置由打包脚本注入 APK。飞书的回调 URL 由当前 Cloudflare Quick Tunnel 决定。

### 2. Bot 配置信息

表/实体：

```text
bot_configs
```

保存内容：

- bot id
- bot name
- App ID
- 加壳后的 App Secret
- Verification Token
- Encrypt Key
- 最近事件时间
- 最近消息文本
- 最近回复时间和错误

用途：

- 校验飞书 token 是否有效
- 生成事件订阅 URL 和卡片回调 URL
- 判断飞书事件是否已经打通
- 给 Android/PC 展示 bot health

### 3. OCR 与图片整理信息

表/实体：

```text
vision_traces
```

每次 Android 上传图片或飞书发图片，都会创建一个 trace。

trace 保存：

- `traceId`
- 来源：`android_app`、`feishu_image` 等
- 当前状态：`processing`、`done`、`error`
- 当前阶段：`resource_downloading`、`ocr`、`structuring`、`done` 等
- 进度百分比
- 图片本地路径
- `ocrJson`
- `extractJson`
- 错误码和错误消息

`ocrJson` 保存 OCR 服务返回的完整结果：

- 原文 `plainText`
- 文本块 `blocks`
- 坐标 bbox
- 图片尺寸
- 质量信息

`extractJson` 保存结构化整理结果：

- 摘要 `summary`
- 任务候选 `tasks`
- 链接候选 `links`
- 日报素材 `dailyReportMaterials`
- 风险 `riskFlags`

重要原则：保存任务时不相信前端传回来的任务正文，而是通过 `traceId` 回到数据库读取 `extractJson`。这样可以防止客户端篡改任务内容。

### 4. 任务信息

表/实体：

```text
task_items
```

任务来源：

- Android 手动创建
- 飞书自然语言创建
- OCR trace 中的任务候选保存
- AgentRun 中的任务候选保存

任务字段：

- 标题
- 负责人
- 截止时间文本
- 优先级
- 状态
- 来源
- traceId 或 runId
- 置信度

任务状态只有四种：

```text
todo
in_progress
done
ignored
```

前端不直接修改复杂业务对象，只调用 API：

```http
GET   /api/v1/tasks
POST  /api/v1/tasks
POST  /api/v1/tasks/from-trace/{traceId}
POST  /api/v1/agent/runs/{runId}/tasks
PATCH /api/v1/tasks/{taskId}/status
```

### 5. AgentRun 信息

表/实体：

```text
agent_runs
```

每次命令都会保存一条 AgentRun。

保存内容：

- runId
- command
- intent
- module
- status
- summary
- projectPath
- source
- 完整 `payloadJson`

支持的 intent：

- `project_review`：项目只读扫描
- `research`：论文检索
- `digest`：群消息/日报素材总结
- `task_create`：任务创建
- `memory`：长期记忆

当前 AgentRun 是“项目助理”能力，不等于 Codex 真正写代码。它可以扫描项目、总结风险、生成任务候选，但默认不从飞书或手机直接改文件。

### 6. 长期记忆信息

表/实体：

```text
memory_items
```

保存内容：

- 标题
- 内容
- 分类
- 来源
- 创建时间

用途：

- 保存产品决策
- 保存群总结关键结论
- 保存论文结论
- 保存 OCR 或人工输入的长期上下文

API：

```http
GET  /api/v1/memory
POST /api/v1/memory
```

## 信息流转总图

```text
Android / PC Web / Feishu
  -> Spring Boot REST API
  -> Admin Token 或 Feishu Verification Token 校验
  -> 按类型进入：
       AgentRunService
       VisionPipelineService
       TaskService
       MemoryService
       BotConfigService
  -> 保存到 H2/PostgreSQL
  -> 返回 JSON、飞书文本或飞书卡片
```

图片类信息：

```text
图片
  -> vision_traces 创建 trace
  -> OCR Service
  -> ocrJson
  -> RuleBasedExtractService
  -> extractJson
  -> 任务候选 / 日报素材 / 链接 / 风险
  -> Android 或飞书卡片展示
  -> 用户确认后写入 task_items
```

文本类信息：

```text
文本命令
  -> AgentRunService
  -> intent 分类
  -> 调用项目扫描、论文源、LLM 或规则抽取
  -> agent_runs 保存完整 payload
  -> 任务候选可写入 task_items
  -> 重要结论可写入 memory_items
```

## PC 前端应如何补

PC 前端应该复用现有后端 API，不重新实现业务逻辑。

最小可用版本建议：

1. 建一个 `/web` 或 `/dashboard` 页面。
2. 登录先输入 Admin Token，保存在浏览器 localStorage。
3. 所有请求带：

   ```http
   Authorization: Bearer <admin-token>
   ```

4. 首页调用：

   ```http
   GET /api/v1/health
   GET /api/v1/setup/readiness
   GET /api/v1/setup/queues
   GET /api/v1/workbench/overview
   ```

5. OCR 页调用：

   ```http
   POST /api/v1/vision/upload
   GET /api/v1/vision/results/{traceId}
   POST /api/v1/tasks/from-trace/{traceId}
   ```

6. Agent 页调用：

   ```http
   POST /api/v1/agent/runs
   GET /api/v1/agent/runs
   POST /api/v1/agent/runs/{runId}/tasks
   ```

7. 任务页调用：

   ```http
   GET /api/v1/tasks
   PATCH /api/v1/tasks/{taskId}/status
   ```

这样 PC 前端只是展示和操作层，信息管理仍由 Spring Boot 后端统一负责。

## 对外回答口径

可以这样回答：

> 前端目前分成 Android 原生端和飞书机器人交互端。Android 负责手机上的命令、OCR、任务和飞书状态；飞书端通过聊天消息和互动卡片完成群内输入、图片整理、任务保存和日报素材生成。PC Web 工作台目前还没有正式实现，后端已经提供了工作台、任务、OCR、AgentRun、记忆和飞书状态 API，下一步可以基于这些 API 做 PC 页面。
>
> 信息管理由 Spring Boot 后端统一完成。图片、命令、任务、记忆和飞书配置都会进入后端，分别保存到 `vision_traces`、`agent_runs`、`task_items`、`memory_items`、`bot_configs`。OCR 原文和结构化结果会以 JSON 形式保存，保存任务时后端只信任数据库里的 trace/run 结果，不信任前端回传的任务正文。这样可以保证 Android、飞书和未来 PC Web 使用同一套数据和权限规则。
