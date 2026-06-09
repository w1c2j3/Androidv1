# 视流 AI V1 项目链路交接说明

本文给没有参与开发的人快速理解和对外回答项目使用。内容基于 2026-06-01 当前仓库代码、`README.md`、`docs/api-design.md`、`docs/rules.md` 和 `docs/ai-handoff-2026-05-31-restart.md`。

## 当前交接状态

- 2026-05-31 handoff 记录的最后一次验证结果：OCR 单测 47/47 通过，后端测试 19/19 通过，Android build 通过，Quick Tunnel 公开链路 E2E 44 项检查 0 失败。
- 上次真实 PNG OCR 上传经过公网 tunnel 跑通，识别到 OCR text blocks 525 个，抽取任务候选 19 个。
- 上次 E2E 使用 `SHILIU_LLM_ENABLED=false`，因此 OCR、OpenAlex 论文检索、任务保存、记忆保存、队列状态和项目扫描是真实链路，模型回复没有开启。
- 当前所有测试服务按 handoff 已停止；旧 Quick Tunnel URL 已失效，重启后必须使用新 URL。
- 工作区是 dirty 且有未跟踪文件；交接或继续开发时不要回滚别人留下的改动。
- 本仓库执行 shell 命令时按项目指令加 `rtk` 前缀。

## 一句话说明

视流 AI V1 是一个 Android-first 的移动端 AI 项目助理：手机端和飞书机器人把用户命令、群消息、图片截图提交给本地后端；后端统一做鉴权、项目只读扫描、论文检索、OCR、任务抽取、记忆保存和飞书回复；OCR 由独立 FastAPI + PaddleOCR 服务完成；需要模型总结时通过 OpenAI 兼容接口调用外部 LLM。

## 当前项目由哪些部分组成

| 模块 | 位置 | 作用 | 运行时身份 |
| --- | --- | --- | --- |
| Android App | `android/` | 手机端命令中心、任务中心、知识库、OCR 上传、飞书状态页和设置页 | 客户端 |
| Spring Boot Backend | `backend/` | 统一 API、Admin Token 鉴权、AgentRun、任务/记忆持久化、飞书回调、OCR 编排 | 核心编排层 |
| OCR Service | `ocr-service/` | FastAPI 服务，接收图片 multipart，调用 PaddleOCR，返回统一 OCR JSON | 独立识别服务 |
| 飞书机器人链路 | `backend/src/main/java/com/shiliuai/service/feishu/` | 接收飞书事件、发送文本/卡片、下载飞书图片、处理卡片按钮回调 | 外部入口 |
| 数据库 | 本地默认 H2，Docker 可用 PostgreSQL | 保存 bot 配置、AgentRun、OCR trace、任务、记忆 | 状态存储 |
| Cloudflare Quick Tunnel | `scripts/run-remote-mobile-backend.sh` | 给真机和飞书提供临时 HTTPS 公网地址 | 测试通道 |

## 总体链路图

```text
Android App
  -> /api/v1/**  Authorization: Bearer <admin-token>
  -> Spring Boot Backend
       -> H2/PostgreSQL 保存 AgentRun、任务、记忆、OCR trace、bot 配置
       -> 本地项目目录，只读扫描允许路径
       -> OCR Service: POST /ocr
       -> 论文数据源: arXiv，失败时 OpenAlex 兜底
       -> LLM: OpenAI compatible /v1/chat/completions
       -> 飞书 OpenAPI: 发消息、更新卡片、读历史消息、下载图片

Feishu
  -> /feishu/events/{botId}
  -> Spring Boot Backend
  -> AgentRun / VisionPipeline / TaskService / FeishuCardService
  -> Feishu OpenAPI 回复文本或卡片
```

## 信息传递格式总览

整个项目不是一个单一协议，实际是几种格式混合：

| 起点 -> 终点 | 传输方式 | 内容格式 | 说明 |
| --- | --- | --- | --- |
| Android -> Backend 普通 API | HTTP JSON | `application/json` | 命令、任务、记忆、设置检测结果 |
| Android -> Backend 图片上传 | HTTP multipart | `multipart/form-data` | 图片文件 + query 参数 `source`、`sceneHint` |
| Backend -> Android | HTTP JSON | `application/json` | AgentRun、OCR 进度、任务、记忆、readiness |
| Backend -> OCR Service | HTTP multipart | `multipart/form-data` | 图片文件 + `traceId` + `source` + JSON 字符串 `hints` |
| OCR Service -> Backend | HTTP JSON | `application/json` | `OcrResult`：原文、文本块、bbox、置信度 |
| Backend 内部 OCR -> 抽取 | Java DTO | `ExtractRequest` 对象 | `plainText` + `OcrResult` + options |
| Backend -> DB | JPA Entity | 表字段 + JSON 字符串 | `ocrJson`、`extractJson`、`payloadJson` 保存完整结果 |
| Feishu -> Backend | HTTP JSON | 飞书事件 JSON | `challenge`、消息事件、卡片回调 |
| Backend -> Feishu | HTTP JSON | 飞书 OpenAPI JSON | 文本消息或 interactive card |
| Backend -> LLM | HTTP JSON | OpenAI compatible chat completions | `model`、`messages`、`temperature`、`max_tokens` |
| Backend -> 论文源 | HTTP | arXiv XML / OpenAlex JSON | arXiv Atom API，失败时 OpenAlex works API |

### 1. Android 调 AgentRun 的 JSON

Android 命令中心、OCR 分发、论文收集、群消息总结最终都会进入这个结构：

```http
POST /api/v1/agent/runs
Authorization: Bearer <admin-token>
Content-Type: application/json
```

```json
{
  "command": "/digest 总结以下内容",
  "projectPath": "/home/chase/GitHub/shiliu-ai-v1",
  "source": "android",
  "contextText": "这里放真实群消息、OCR 原文或用户粘贴文本",
  "chatId": null,
  "traceId": "trace_20260531_android_upload_001",
  "saveTasks": false
}
```

字段口径：

| 字段 | 类型 | 作用 |
| --- | --- | --- |
| `command` | string | 用户命令，后端靠它判断 intent |
| `projectPath` | string | 要分析的绝对路径，必须在允许根目录内 |
| `source` | string | 来源，如 `android`、`feishu_text` |
| `contextText` | string | 真实上下文文本；Digest 和 OCR 分发必须放这里 |
| `chatId` | string | 飞书群 ID，飞书文本命令会带 |
| `traceId` | string | OCR trace 关联 ID |
| `saveTasks` | boolean | 创建 AgentRun 后是否直接保存任务候选 |

重要：`contextText` 是顶层字段，不是 `context.text`。

AgentRun 返回 JSON：

```json
{
  "runId": "run_20260531_001",
  "status": "done",
  "intent": "digest",
  "module": "Digest Agent",
  "command": "/digest 总结以下内容",
  "projectPath": "/home/chase/GitHub/shiliu-ai-v1",
  "source": "android",
  "summary": "已基于真实输入整理...",
  "requiresConfirmation": false,
  "createdAt": "2026-05-31T10:00:00Z",
  "completedAt": "2026-05-31T10:00:01Z",
  "steps": ["解析命令和意图", "选择模块：Digest Agent"],
  "logs": ["received command from android", "status=done"],
  "nextSteps": ["基于真实输入检查摘要是否遗漏关键任务。"],
  "risks": [
    { "type": "digest_source_missing", "message": "群总结必须读取真实飞书消息或用户提供的真实文本。" }
  ],
  "tasks": [
    {
      "tempId": "task_tmp_1",
      "title": "整理客户反馈",
      "owner": "未指定",
      "dueText": "本周",
      "dueAt": null,
      "priority": "medium",
      "sourceBlockIds": [],
      "confidence": 0.88,
      "status": "pending_confirm"
    }
  ],
  "papers": []
}
```

### 2. Android 上传图片的 multipart

Android 不是把图片转 base64，而是直接 multipart 上传文件：

```http
POST /api/v1/vision/upload?source=android_upload&sceneHint=auto
Authorization: Bearer <admin-token>
Content-Type: multipart/form-data; boundary=...
```

multipart 内容：

```text
file: android-upload.jpg
```

上传后立即返回：

```json
{
  "traceId": "trace_20260531_android_upload_001",
  "status": "processing",
  "message": "已接收图片，等待后台识别",
  "pollUrl": "/api/v1/vision/results/trace_20260531_android_upload_001",
  "nextStep": "poll_result"
}
```

Android 后续轮询：

```http
GET /api/v1/vision/results/{traceId}
Authorization: Bearer <admin-token>
```

处理中返回：

```json
{
  "traceId": "trace_20260531_android_upload_001",
  "status": "processing",
  "stage": "ocr",
  "progress": 45,
  "message": "正在进行 OCR 识别",
  "errorCode": null,
  "scene": null
}
```

完成后返回：

```json
{
  "traceId": "trace_20260531_android_upload_001",
  "status": "done",
  "stage": "done",
  "progress": 100,
  "message": "识别完成",
  "scene": "chat_screenshot",
  "summary": {
    "title": "图片内容整理",
    "bullets": ["请本周完成 OCR 联调。"],
    "confidence": 0.82
  },
  "tasks": [
    {
      "tempId": "task_tmp_1",
      "title": "本周完成 OCR 联调",
      "owner": "未指定",
      "dueText": "本周",
      "dueAt": null,
      "priority": "high",
      "sourceBlockIds": ["b1"],
      "confidence": 0.87,
      "status": "pending_confirm"
    }
  ],
  "links": [
    { "url": "https://example.com", "title": "识别链接", "confidence": 0.95 }
  ],
  "ocr": {
    "plainText": "请本周完成 OCR 联调",
    "blockCount": 1,
    "width": 1170,
    "height": 2532,
    "blocks": [
      {
        "id": "b1",
        "type": "text_line",
        "text": "请本周完成 OCR 联调",
        "bbox": [10, 20, 500, 60],
        "confidence": 0.96
      }
    ],
    "quality": {}
  }
}
```

失败时返回：

```json
{
  "traceId": "trace_20260531_android_upload_001",
  "status": "error",
  "stage": "error",
  "progress": 100,
  "message": "OCR 或抽取失败：...",
  "errorCode": "OCR_FAILED"
}
```

### 3. Backend 调 OCR Service 的 multipart

后端收到 Android 或飞书图片后，会把本地图片文件转发给 OCR 服务：

```http
POST http://127.0.0.1:19200/ocr
Content-Type: multipart/form-data
```

multipart 字段：

| 字段 | 类型 | 示例 | 说明 |
| --- | --- | --- | --- |
| `file` | file | 图片二进制 | 后端已保存的本地图片 |
| `traceId` | string | `trace_...` | 全链路追踪 ID |
| `source` | string | `android_upload` / `feishu_image` | 图片来源 |
| `hints` | string(JSON) | `{"expectedScene":"auto","language":"zh-CN"}` | OCR 提示参数，注意它是表单里的 JSON 字符串 |

实际 hints：

```json
{
  "expectedScene": "auto",
  "language": "zh-CN",
  "enableTable": true,
  "enableChatBubble": true
}
```

OCR 服务返回：

```json
{
  "traceId": "trace_20260531_android_upload_001",
  "imageType": "screenshot",
  "width": 1170,
  "height": 2532,
  "plainText": "请本周完成 OCR 联调",
  "blocks": [
    {
      "id": "b1",
      "type": "text_line",
      "text": "请本周完成 OCR 联调",
      "bbox": [10, 20, 500, 60],
      "confidence": 0.96
    }
  ],
  "quality": {}
}
```

### 4. Backend 内部 OCR 抽取格式

OCR 返回后，后端内部把 OCR 结果包装成 `ExtractRequest`：

```json
{
  "traceId": "trace_20260531_android_upload_001",
  "scene": "chat_screenshot",
  "plainText": "请本周完成 OCR 联调",
  "ocrResult": {
    "traceId": "trace_20260531_android_upload_001",
    "imageType": "screenshot",
    "width": 1170,
    "height": 2532,
    "blocks": [],
    "plainText": "请本周完成 OCR 联调",
    "quality": {}
  },
  "options": {
    "extractTasks": true,
    "extractLinks": true,
    "generateDailyReportMaterial": true,
    "language": "zh-CN"
  }
}
```

抽取结果 `ExtractResult` 会保存进数据库 `vision_traces.extractJson`：

```json
{
  "traceId": "trace_20260531_android_upload_001",
  "summary": {
    "title": "图片内容整理",
    "bullets": ["请本周完成 OCR 联调。"],
    "confidence": 0.82
  },
  "tasks": [],
  "links": [],
  "dailyReportMaterials": ["整理图片内容：图片内容整理。"],
  "riskFlags": []
}
```

同时原始 OCR 结果保存进 `vision_traces.ocrJson`。

### 5. 保存任务的 JSON

从 OCR trace 保存任务：

```http
POST /api/v1/tasks/from-trace/{traceId}
Authorization: Bearer <admin-token>
Content-Type: application/json
```

请求体可为空对象，也可以选部分任务：

```json
{
  "selectedTaskTempIds": ["task_tmp_1", "task_tmp_2"],
  "override": {
    "owner": "我",
    "dueAt": "2026-06-05T10:00:00Z"
  }
}
```

返回：

```json
{
  "savedCount": 1,
  "tasks": [
    {
      "id": "task_20260531_001",
      "title": "本周完成 OCR 联调",
      "owner": "我",
      "dueAt": "2026-06-05T10:00:00Z",
      "dueText": "本周",
      "priority": "high",
      "status": "todo",
      "source": "android_upload",
      "traceId": "trace_20260531_android_upload_001",
      "confidence": 0.87,
      "createdAt": "2026-05-31T10:00:00Z"
    }
  ]
}
```

### 6. 飞书事件 JSON

飞书 URL verification：

```json
{
  "challenge": "challenge-token"
}
```

后端原样返回：

```json
{
  "challenge": "challenge-token"
}
```

飞书文本消息事件关键结构：

```json
{
  "header": {
    "event_type": "im.message.receive_v1",
    "token": "<verification-token>"
  },
  "event": {
    "message": {
      "message_type": "text",
      "chat_id": "oc_xxx",
      "message_id": "om_xxx",
      "content": "{\"text\":\"@视流助手 /plan 分析项目下一步\"}"
    }
  }
}
```

注意：飞书的 `content` 本身是一个 JSON 字符串，后端会再 parse 一次。

飞书图片消息事件关键结构：

```json
{
  "header": {
    "event_type": "im.message.receive_v1",
    "token": "<verification-token>"
  },
  "event": {
    "message": {
      "message_type": "image",
      "chat_id": "oc_xxx",
      "message_id": "om_xxx",
      "content": "{\"image_key\":\"img_xxx\"}"
    }
  }
}
```

后端收到图片后，用 `message_id` + `image_key` 去飞书 OpenAPI 下载图片。

### 7. Backend 发给飞书的消息格式

普通文本消息：

```http
POST /im/v1/messages?receive_id_type=chat_id
Authorization: Bearer <tenant_access_token>
Content-Type: application/json
```

```json
{
  "receive_id": "oc_xxx",
  "msg_type": "text",
  "content": "{\"text\":\"pong\"}",
  "uuid": "<random-uuid>"
}
```

飞书卡片消息：

```json
{
  "receive_id": "oc_xxx",
  "msg_type": "interactive",
  "content": "{\"config\":{\"wide_screen_mode\":true,\"update_multi\":true},\"header\":{...},\"elements\":[...]}"
}
```

卡片按钮的 `value` 会带 action 和 traceId：

```json
{
  "action": "save_tasks",
  "traceId": "trace_20260531_feishu_image_001"
}
```

飞书卡片回调时，后端读取：

```json
{
  "event": {
    "action": {
      "value": {
        "action": "save_tasks",
        "traceId": "trace_20260531_feishu_image_001"
      }
    }
  }
}
```

### 8. Backend 调 LLM 的 JSON

后端使用 OpenAI 兼容 `/v1/chat/completions`：

```http
POST <SHILIU_LLM_API_BASE_URL>/v1/chat/completions
Authorization: Bearer <SHILIU_LLM_API_KEY>
Content-Type: application/json
```

```json
{
  "model": "deepseek-v3.2",
  "messages": [
    {
      "role": "system",
      "content": "你是视流 AI 的飞书助手..."
    },
    {
      "role": "user",
      "content": "请把下面真实输入整理成摘要、决策、任务、风险和下一步..."
    }
  ],
  "temperature": 0.4,
  "max_tokens": 600
}
```

后端读取响应里的：

```json
{
  "choices": [
    {
      "message": {
        "content": "模型回复文本"
      }
    }
  ]
}
```

### 9. Backend 保存到数据库的格式

数据库不是只存简单字段，有三个重要 JSON 字符串：

| 表 | 字段 | 保存内容 |
| --- | --- | --- |
| `agent_runs` | `payloadJson` | 完整 `AgentRunDto` |
| `vision_traces` | `ocrJson` | 完整 `OcrResult` |
| `vision_traces` | `extractJson` | 完整 `ExtractResult` |

因此 Android 或飞书后续保存任务时，不靠前端传任务正文，而是用 `traceId` 回到数据库读取 `extractJson`。

## 必须先记住的边界

- `/api/v1/**` 除 `GET /api/v1/health` 外都需要 `Authorization: Bearer <admin-token>`。
- 不使用 `X-Shiliu-Admin-Token`，这个请求头会导致 401。
- 公网测试不能使用默认 `dev-admin-token`。
- Quick Tunnel 地址每次重启都会变化，旧地址无效，Android 和飞书回调都要换新地址。
- 真实密钥不能写入仓库：飞书 App Secret、LLM API Key、Admin Token 都只能用环境变量或运行时输入。
- 没有真实数据源时不能造演示数据，要返回 `needs_data_source`、`data_source_error` 或明确错误。
- Android 端默认只触发只读分析和保存任务/记忆，不直接开放手机端写代码。
- 项目扫描只能访问后端允许路径，默认是 `/home/chase/GitHub/shiliu-ai-v1`。

## 关键配置

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | 后端端口 |
| `SHILIU_ADMIN_TOKEN` | `dev-admin-token` | Admin API token，公网必须改强 token |
| `SHILIU_PUBLIC_BASE_URL` | `http://localhost:8080` | 生成飞书回调 URL 时使用 |
| `SHILIU_AGENT_ALLOWED_PROJECT_ROOTS` | `/home/chase/GitHub/shiliu-ai-v1` | AgentRun 允许扫描的根目录，逗号分隔 |
| `SHILIU_OCR_HTTP_ENDPOINT` | `http://localhost:9000/ocr` | 后端调用 OCR 服务地址 |
| `SHILIU_LLM_ENABLED` | `true` | 是否启用模型调用 |
| `SHILIU_LLM_API_BASE_URL` | 空 | OpenAI 兼容接口 base URL |
| `SHILIU_LLM_API_KEY` | 空 | LLM API key |
| `SHILIU_LLM_MODEL` | `deepseek-v3.2` | 模型名 |
| `SHILIU_FEISHU_API_BASE_URL` | `https://open.feishu.cn/open-apis` | 飞书 OpenAPI 地址 |

## 链路 1：Android 设置和连通性检查

1. Android 设置页保存三个值：Backend URL、Admin Token、projectPath。
2. Android API client 每个请求都加：

   ```http
   Authorization: Bearer <admin-token>
   ```

3. 点击保存/刷新后，Android 调：

   ```http
   GET /api/v1/setup/readiness
   GET /api/v1/setup/queues
   GET /api/v1/tasks
   GET /api/v1/memory
   ```

4. `readiness` 负责告诉用户后端、OCR、飞书 bot、飞书事件回调是否就绪。
5. `queues` 返回真实线程池状态，包括 OCR/Vision 队列和飞书事件队列是否 `idle`、`busy`、`overloaded`。

常见回答：

- 401 是 Admin Token 不对或请求头格式不对。
- OCR 状态字段叫 `ocrHealthy`，不是 `ocrOk`。
- 首页概览 `/api/v1/workbench/overview` 不包含 readiness，要看 `/api/v1/setup/readiness`。

## 链路 2：Android / 飞书文本命令到 AgentRun

统一入口：

```http
POST /api/v1/agent/runs
```

请求关键字段：

```json
{
  "command": "/plan 分析项目下一步",
  "projectPath": "/home/chase/GitHub/shiliu-ai-v1",
  "source": "android",
  "contextText": "真实上下文文本"
}
```

注意：群总结或 OCR 分发给 Digest 时，真实文本必须放顶层 `contextText`，不是 `context.text`。

后端执行过程：

1. `AdminTokenInterceptor` 校验 Bearer token。
2. `AgentRunController` 接收请求，交给 `AgentRunService.createRun`。
3. `AgentRunService` 根据命令推断 intent：
   - `/paper`、`论文`、`benchmark` -> `research`
   - `/digest`、`群总结`、`群消息` -> `digest`
   - `/task`、`添加一个任务`、`创建任务` -> `task_create`
   - `/remember`、`记住` -> `memory`
   - 其他默认 -> `project_review`
4. `AgentAccessPolicy` 校验 `projectPath` 必须是绝对路径，且位于允许根目录内。
5. 不同 intent 进入不同实现：
   - `project_review`：`ProjectReviewService` 只读扫描真实项目文件、Git 分支、dirty 状态和测试目录。
   - `research`：`ArxivPaperSearchService` 查 arXiv；arXiv 限流或失败时用 OpenAlex 兜底。
   - `digest`：优先用 `contextText` 或命令内联文本；有 LLM 时调用模型，没有 LLM 时基于真实文本做降级摘要；没有真实文本返回 `needs_data_source`。
   - `task_create`：生成任务候选。
   - `memory`：写入知识库。
6. AgentRun 完成后保存到 `agent_runs`，Android 可查最近 20 条。
7. 如果用户点击“保存为任务”，调用：

   ```http
   POST /api/v1/agent/runs/{runId}/tasks
   ```

   后端把 AgentRun 的任务候选保存到 `task_items`。

## 链路 3：Android 图片上传到 OCR 结果

入口：

```http
POST /api/v1/vision/upload?source=android_upload&sceneHint=auto
Content-Type: multipart/form-data
```

后端执行过程：

1. `VisionController.upload` 接收图片。
2. `FileStorageService` 把图片保存到后端文件目录。
3. `VisionPipelineService.startUpload` 创建 `vision_traces` 记录：
   - `status=processing`
   - `stage=received`
   - `progress=5`
4. 后端立即返回 `traceId` 和 `pollUrl`，Android 不等待 OCR 完成。
5. OCR 任务进入 `visionTaskExecutor` 后台队列。
6. 后台阶段依次更新：
   - `resource_downloaded`
   - `ocr`
   - `ocr_done`
   - `structuring`
   - `done`
7. `HttpOcrProvider` 调外部 OCR 服务：

   ```http
   POST <SHILIU_OCR_HTTP_ENDPOINT>
   Content-Type: multipart/form-data
   fields: file, traceId, source, hints
   ```

8. OCR 服务返回 `plainText`、`blocks`、图片宽高和质量信息。
9. `SceneClassifier` 判断场景。
10. `RuleBasedExtractService` 基于 OCR 文本抽取摘要、任务候选、链接、日报素材和风险。
11. 后端把 `ocrJson` 和 `extractJson` 存回 `vision_traces`。
12. Android 轮询：

   ```http
   GET /api/v1/vision/results/{traceId}
   ```

13. 完成后 Android 可以做四件事：
   - 保存 OCR 任务候选：`POST /api/v1/tasks/from-trace/{traceId}`
   - 把 OCR 原文作为 `contextText` 交给 Digest AgentRun
   - 把 OCR 原文作为项目分析证据交给 Project AgentRun
   - 保存 OCR 原文到知识库

如果 OCR 队列满，trace 会进入 `error`，`errorCode=VISION_QUEUE_FULL`，前端提示稍后重试。

## 链路 4：OCR 服务本身

OCR 服务在 `ocr-service/`，是 FastAPI 应用：

```http
GET  /health
POST /ocr
```

实现过程：

1. `/ocr` 接收 multipart 图片，校验大小，写入临时文件。
2. `PaddleOcrEngine` 懒加载 PaddleOCR，默认使用 PP-OCRv5 mobile 配置。
3. `converter` 把 PaddleOCR 原始输出转换成统一 `OcrResult`。
4. 服务返回后删除临时文件。

默认本地启动：

```bash
OCR_PORT=19200 rtk ./scripts/run-ocr-service.sh
```

后端需要对应配置：

```bash
SHILIU_OCR_HTTP_ENDPOINT=http://127.0.0.1:19200/ocr
```

## 链路 5：飞书机器人注册和 readiness

注册入口：

```http
POST /api/v1/bots/register
```

注册过程：

1. 用户提交 botName、App ID、App Secret、Verification Token、Encrypt Key 等。
2. `BotConfigService.register` 先用 App ID / Secret 获取飞书 `tenant_access_token`，能拿到才保存。
3. App Secret 用 `SecretEncryptor` 做 Base64 占位封装后保存，不应把真实 secret 写入代码或文档。
4. 返回两个 URL：

   ```text
   <public-base-url>/feishu/events/{botId}
   <public-base-url>/feishu/card-callback/{botId}
   ```

5. 用户把这两个 URL 配到飞书开放平台。
6. 飞书 URL verification 请求包含 `challenge` 时，后端直接返回同一个 challenge。
7. 群里发送 `@视流助手 /ping` 后，后端记录 `lastEventAt`，`eventCallbackVerified=true`。
8. Android 通过 `/api/v1/setup/readiness` 展示 bot 是否注册、token 是否有效、事件回调是否验证。

## 链路 6：飞书文本消息

飞书入口：

```http
POST /feishu/events/{botId}
```

执行过程：

1. `FeishuEventController` 接收飞书事件。
2. `FeishuEventService` 校验 bot 是否存在、Verification Token 是否匹配。
3. 只处理 `im.message.receive_v1`。
4. 文本消息分三类：
   - `/ping`：异步回复 `pong`。
   - `/plan`、`/bug`、`/paper`、`/digest`、`/remember`、`/task`：进入 AgentRun。
   - “添加任务/创建任务”等自然语言：直接调用 `TaskService.createTextTask`。
5. `/digest` 会尝试调用飞书 OpenAPI 读取当前群最近 50 条文本消息，作为 `contextText` 传给 AgentRun。
6. 普通文本如果不是命令，则交给 LLM；LLM 未配置时返回降级提示。

飞书文本事件先进 `feishuEventTaskExecutor` 队列。队列满时不阻塞飞书回调，会返回演示保护提示。

## 链路 7：飞书图片和卡片按钮

飞书图片链路：

1. 飞书图片事件进入 `/feishu/events/{botId}`。
2. 后端创建 `vision_traces`，source 为 `feishu_image`。
3. 先发一张“处理中”飞书卡片。
4. 后台通过飞书 OpenAPI 下载图片资源：

   ```http
   GET /im/v1/messages/{messageId}/resources/{fileKey}?type=image
   ```

5. 下载后的图片进入同一条 `VisionPipelineService`：
   - OCR
   - 场景判断
   - 规则抽取
   - 保存 trace
6. 每个阶段尝试更新原“处理中”卡片。
7. 完成后把卡片替换成结果卡片；失败则替换成错误卡片。

卡片回调入口：

```http
POST /feishu/card-callback/{botId}
```

支持动作：

| action | 作用 |
| --- | --- |
| `open_task_confirm` | 返回任务确认卡 |
| `save_tasks` | 从 trace 的 `extractJson` 保存任务 |
| `ignore` | 返回忽略 toast |
| `create_report_material` | 返回日报素材卡 |
| `set_reminder` | 当前返回下一版提示 |
| `private_result` | 当前返回权限策略下一步提示 |

关键安全点：保存任务时后端只信任 trace 中已经保存的 `extractJson`，不信任客户端或卡片按钮传回来的任务正文。

## 链路 8：任务、知识库和首页概览

任务 API：

```http
GET   /api/v1/tasks
POST  /api/v1/tasks
POST  /api/v1/tasks/from-trace/{traceId}
PATCH /api/v1/tasks/{taskId}/status
```

任务来源包括：

- Android 手动创建。
- 飞书文本自然语言创建。
- AgentRun 任务候选保存。
- OCR trace 任务候选保存。

任务状态只有四种：

```text
todo, in_progress, done, ignored
```

知识库 API：

```http
GET  /api/v1/memory
POST /api/v1/memory
```

知识库保存长期上下文，例如产品决策、论文结论、群总结关键点、OCR 原文。

首页概览 API：

```http
GET /api/v1/workbench/overview
GET /api/v1/vision/traces?limit=20
GET /api/v1/vision/files/{traceId}
```

概览只统计 trace 和任务数据，不负责 readiness。

## 数据模型口径

| 表/实体 | 保存内容 |
| --- | --- |
| `bot_configs` | 飞书 bot 配置、加壳后的 secret、Verification Token、最近事件和回复状态 |
| `agent_runs` | 每次 AgentRun 的命令、intent、module、summary、完整 payload |
| `vision_traces` | 图片处理 trace、阶段、进度、OCR JSON、抽取 JSON、错误码 |
| `task_items` | 任务标题、负责人、状态、来源、traceId、置信度 |
| `memory_items` | 长期记忆标题、内容、分类、来源 |

默认开发用 H2 文件库：

```text
jdbc:h2:file:./data/shiliu-v1
```

Docker Compose 可切到 PostgreSQL。

## 本地和远程启动口径

本地同机开发：

```bash
rtk ./scripts/run-ocr-service.sh
rtk ./scripts/run-backend.sh
```

真机或飞书公网测试：

```bash
mkdir -p .tmp
openssl rand -hex 24 > .tmp/e2e-admin-token
PATH="$PWD/.tmp/bin:$PATH" \
SERVER_PORT=28280 \
SPRING_DATASOURCE_URL='jdbc:h2:file:./data/e2e-tunnel-28280' \
SHILIU_ADMIN_TOKEN="$(cat .tmp/e2e-admin-token)" \
SHILIU_OCR_HTTP_ENDPOINT='http://127.0.0.1:19200/ocr' \
SHILIU_AGENT_ALLOWED_PROJECT_ROOTS='/home/chase/GitHub/shiliu-ai-v1' \
SHILIU_LLM_ENABLED=false \
rtk ./scripts/run-remote-mobile-backend.sh
```

脚本会输出：

```text
Backend URL : https://<new-random>.trycloudflare.com
Admin Token : <generated-token>
```

这两个值要填入 Android 设置页。飞书回调 URL 也必须使用这个新的 HTTPS 地址。

## 最小验证清单

服务启动后，按这个顺序验证：

1. `GET /api/v1/health`，不需要 token。
2. `GET /api/v1/setup/queues`，需要 Bearer token。
3. `GET /api/v1/setup/readiness`，需要 Bearer token，看 `ocrHealthy`。
4. `POST /api/v1/agent/runs`，用顶层 `contextText` 测 digest 或 project review。
5. `POST /api/v1/vision/upload`，拿到 `traceId`。
6. `GET /api/v1/vision/results/{traceId}`，轮询到 `done` 或明确 `error`。
7. `POST /api/v1/tasks/from-trace/{traceId}`，保存 OCR 任务候选。
8. `POST /feishu/events/{botId}`，用 challenge payload 验证飞书 URL。

## 对外常见问答

**这个项目到底做什么？**

它把手机、飞书群和本地项目工作流连起来：用户可以在 Android 或飞书里发命令、发截图，后端会做真实项目扫描、OCR、任务抽取、论文检索、群消息总结，并把结果变成任务或知识库内容。

**为什么说是 Android-first？**

第一入口是 Android App。App 负责设置后端地址、Admin Token 和项目路径，也负责命令中心、任务、知识库、OCR 上传和飞书状态查看。后端没有单独 Web 工作台页面。

**后端是不是直接改代码？**

当前手机端默认只做只读分析、任务生成和记忆保存。项目扫描会读文件和 Git 状态，但不会直接从手机端写代码。真正修改代码需要人工确认。

**OCR 是怎么跑的？**

图片先传到后端生成 trace，后端立刻返回；后台队列再把图片发给 OCR 服务。OCR 服务用 PaddleOCR 识别，后端再把 OCR 文本做规则抽取，最后 Android 或飞书卡片展示结果。

**飞书图片和 Android 图片是不是两套逻辑？**

入口不同，但核心复用同一条 `VisionPipelineService`。Android 直接上传图片；飞书先下载图片资源，再进入同一条 OCR 和抽取链路。

**没有配置大模型会怎样？**

OCR、任务保存、项目扫描、论文检索仍可用。Digest 或普通文本回复会降级，返回“模型未配置/暂不可用”的真实状态，不会编造模型结果。

**论文从哪里来？**

优先 arXiv Atom API；arXiv 限流或失败时用 OpenAlex 兜底。失败时返回 `data_source_error`。

**群总结的数据从哪里来？**

Android 可以直接传 `contextText`。飞书 `/digest` 会尝试读取当前群最近文本消息。如果没有真实文本，就返回 `needs_data_source`。

**为什么飞书或手机有时要换 URL？**

Quick Tunnel 是临时公网地址。每次重启都会生成新域名，旧域名不能复用，所以 Android Backend URL 和飞书回调 URL 都要更新。

**怎么判断系统就绪？**

看 `/api/v1/setup/readiness`。核心字段是 `backendOk`、`ocrHealthy`、`botRegistered`、`tokenValid`、`eventCallbackVerified` 和 `ready`。

**为什么要看队列？**

OCR 和飞书事件是后台任务。`/api/v1/setup/queues` 返回真实 active、queue、completed 数字。`busy` 表示排队中；`overloaded` 表示触发演示保护，系统会快速返回明确提示。

**任务保存为什么可信？**

从 OCR 保存任务时，后端只读取数据库 trace 中保存的 `extractJson`，不会相信客户端或飞书卡片按钮传来的任务正文，避免篡改。

## 代码定位速查

| 想了解 | 入口文件 |
| --- | --- |
| Android 请求封装 | `android/app/src/main/java/com/shiliuai/app/ShiliuApiClient.java` |
| Android 页面和动作 | `android/app/src/main/java/com/shiliuai/app/MainActivity.java` |
| Admin Token 鉴权 | `backend/src/main/java/com/shiliuai/config/AdminTokenInterceptor.java` |
| AgentRun API | `backend/src/main/java/com/shiliuai/controller/AgentRunController.java` |
| AgentRun 业务 | `backend/src/main/java/com/shiliuai/service/agent/AgentRunService.java` |
| 项目路径限制 | `backend/src/main/java/com/shiliuai/service/agent/AgentAccessPolicy.java` |
| 项目扫描 | `backend/src/main/java/com/shiliuai/service/agent/ProjectReviewService.java` |
| 论文检索 | `backend/src/main/java/com/shiliuai/service/agent/ArxivPaperSearchService.java` |
| OCR 上传 API | `backend/src/main/java/com/shiliuai/controller/VisionController.java` |
| OCR 编排 | `backend/src/main/java/com/shiliuai/service/vision/VisionPipelineService.java` |
| OCR HTTP 调用 | `backend/src/main/java/com/shiliuai/service/vision/HttpOcrProvider.java` |
| 规则抽取 | `backend/src/main/java/com/shiliuai/service/extract/RuleBasedExtractService.java` |
| OCR 服务入口 | `ocr-service/ocr_service/main.py` |
| 飞书事件 | `backend/src/main/java/com/shiliuai/service/feishu/FeishuEventService.java` |
| 飞书图片后台任务 | `backend/src/main/java/com/shiliuai/service/feishu/FeishuImageProcessingJobService.java` |
| 飞书卡片回调 | `backend/src/main/java/com/shiliuai/service/feishu/FeishuCardCallbackService.java` |
| 任务保存 | `backend/src/main/java/com/shiliuai/service/task/TaskService.java` |
| 知识库 | `backend/src/main/java/com/shiliuai/service/memory/MemoryService.java` |
| readiness | `backend/src/main/java/com/shiliuai/service/setup/SetupReadinessService.java` |
| 队列状态 | `backend/src/main/java/com/shiliuai/service/setup/QueueStatusService.java` |
