# 后端 API 设计

本地开发默认 Admin Token：`dev-admin-token`。除 `GET /api/v1/health` 外，`/api/v1/**` 需要请求头：

```http
Authorization: Bearer dev-admin-token
```

## Health

```http
GET /api/v1/health
```

返回 `status`、`service`、`version`、`time`。

## Bot

```http
POST /api/v1/bots/register
GET /api/v1/bots/{botId}/health
GET /api/v1/setup/readiness
GET /api/v1/setup/queues
```

注册接口会先用 App ID 和 App Secret 获取 `tenant_access_token`；校验通过后保存 `bot_configs`，返回飞书事件回调和卡片回调 URL。健康接口会重新校验 token，并返回 `tokenValid`、`eventCallbackVerified` 和下一步提示。

`GET /api/v1/setup/readiness` 给 Android 安装向导使用，统一返回后端、OCR、机器人和事件回调状态：

```json
{
  "backendOk": true,
  "ocrConfigured": true,
  "ocrHealthy": false,
  "botRegistered": true,
  "botId": "bot_20260516_0001",
  "botStatus": "waiting_event",
  "tokenValid": true,
  "eventCallbackVerified": false,
  "ready": false,
  "nextStep": "请在飞书开放平台配置事件回调后，在群里发送 @视流助手 /ping。"
}
```

`GET /api/v1/setup/queues` 给 Android 设置页和大会演示页使用，返回真实后台线程池状态，不返回模拟值：

```json
{
  "healthy": true,
  "status": "idle",
  "message": "后台队列空闲。",
  "pools": [
    {
      "name": "vision",
      "label": "OCR / Vision 后台队列",
      "status": "idle",
      "activeCount": 0,
      "maxPoolSize": 4,
      "queueSize": 0,
      "queueCapacity": 100,
      "completedTaskCount": 12
    },
    {
      "name": "feishu",
      "label": "飞书事件后台队列",
      "status": "idle",
      "activeCount": 0,
      "maxPoolSize": 4,
      "queueSize": 0,
      "queueCapacity": 200
    }
  ]
}
```

当 `status=busy` 时，任务会正常排队；当 `status=overloaded` 时，演示保护会优先快速返回明确提示，避免飞书或 Android 表现为无响应。

## Vision

```http
POST /api/v1/vision/upload
GET /api/v1/vision/results/{traceId}
```

上传接口接收 `multipart/form-data`：

- `file`：图片文件
- `source`：默认 `android_upload`
- `sceneHint`：默认 `auto`

上传接口会先保存图片、创建 trace，然后立即返回 `processing + pollUrl`。OCR 和规则抽取在后台任务中执行，客户端按轮询模型查询结果。
如果 OCR 队列已满，上传仍会返回 trace，但 trace 会被标记为 `error`，`errorCode=VISION_QUEUE_FULL`，前端应提示稍后重试。

处理中响应会返回真实阶段信息：

```json
{
  "traceId": "trace_20260516_android_upload_001",
  "status": "processing",
  "stage": "ocr",
  "progress": 45,
  "message": "正在进行 OCR 识别"
}
```

失败响应会保留 trace，便于前端展示和排查：

```json
{
  "traceId": "trace_20260516_android_upload_001",
  "status": "error",
  "stage": "error",
  "progress": 100,
  "errorCode": "OCR_FAILED",
  "message": "OCR 或抽取失败：..."
}
```

外部 OCR 服务默认地址：

```http
POST http://localhost:9000/ocr
```

OCR 服务接收 `file`、`traceId`、`source`、`hints`，返回统一 `OcrResult`。

## Task

```http
POST /api/v1/tasks
POST /api/v1/tasks/from-trace/{traceId}
GET /api/v1/tasks?status=todo
PATCH /api/v1/tasks/{taskId}/status
```

保存任务时只根据后端 trace 中的 `extractJson` 保存，不信任客户端或卡片回调中传入的任务正文。
任务状态支持 `todo`、`in_progress`、`done`、`ignored`。

## AgentRun

```http
POST /api/v1/agent/runs
GET /api/v1/agent/runs
GET /api/v1/agent/runs/{runId}
POST /api/v1/agent/runs/{runId}/tasks
```

统一命令入口。Android 命令中心、论文收集、群总结、创建任务和记忆都通过 AgentRun 进入后端。AgentRun 已持久化到数据库，重启后可查询最近 20 条运行。

- `/plan`、`/bug`：校验项目路径白名单后，对真实项目文件、Git 状态和测试目录做只读扫描。
- `/paper`：调用 arXiv Atom API 获取真实论文元数据，失败时返回 `data_source_error`。
- `/digest`：Android 可直接传真实文本；飞书文本命令会尝试读取当前群最近文本消息后再总结。

## Memory

```http
GET /api/v1/memory
POST /api/v1/memory
```

用于保存长期项目决策、产品定位、论文结论和群总结中的关键上下文。`/remember` 命令会写入这里。

## Workbench

```http
GET /api/v1/workbench/overview
GET /api/v1/vision/traces?limit=20
GET /api/v1/vision/files/{traceId}
```

这是 Android 首页概览使用的 API，不再提供 Web 工作台页面。

## Feishu

```http
POST /feishu/events/{botId}
POST /feishu/card-callback/{botId}
```

飞书链路直接使用飞书 OpenAPI：

- 获取 tenant token：`/auth/v3/tenant_access_token/internal`
- 发送消息：`/im/v1/messages?receive_id_type=chat_id`
- 更新已发送的消息卡片：`PATCH /im/v1/messages/{message_id}`
- 下载图片资源：`/im/v1/messages/{messageId}/resources/{fileKey}?type=image`

事件接口支持：

- URL verification：请求体包含 `challenge` 时返回 `{"challenge":"..."}`
- 文本 `/ping`：回复 `pong`
- 文本命令：`/plan`、`/bug`、`/paper`、`/digest`、`/remember`、`/task` 会复用 AgentRun 并回复摘要、风险、下一步、任务候选或论文候选
- 文本任务：`添加任务`、`创建任务` 等自然表达会直接创建任务
- 图片消息：创建 trace 并立即返回，后台下载资源、进入 Vision pipeline，并按阶段更新原处理中卡片
- 处理中卡片阶段：`received`、`resource_downloading`、`resource_downloaded`、`ocr`、`ocr_done`、`structuring`、`done`、`error`
- 演示保护：飞书文本/图片任务入队失败时，事件接口仍返回 `code=0`，并尽量发送“稍后重试”的保护提示；飞书发送、token、图片下载、LLM 和 OCR HTTP 调用都设置了有限超时。

卡片回调支持：

- `open_task_confirm`：返回任务确认卡
- `save_tasks`：保存 trace 中所有任务
- `ignore`：返回 toast
- `create_report_material`：返回日报素材卡
