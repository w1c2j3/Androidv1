# 视流 AI 下一步计划与当前状况

更新日期：2026-05-17  
阶段：v1 可运行框架推进中，后端/OCR/Android 本地链路已跑通，飞书真实应用已进入公网回调配置阶段。

## 1. 当前状况

### 1.1 后端

已完成：

- Spring Boot 3 + Java 17 + Maven 后端骨架。
- 健康检查接口：`GET /api/v1/health`。
- 简化 Admin Token 鉴权，用于保护 `/api/v1/**` 接口。
- H2 + Spring Data JPA 数据持久化。
- 机器人注册接口：
  - `POST /api/v1/bots/register`
  - `GET /api/v1/bots/{botId}/health`
- Feishu 真实接入骨架：
  - 获取 `tenant_access_token`
  - 发送文本消息
  - 发送交互卡片
  - 下载消息图片资源
  - 处理事件 challenge
  - 处理 `/ping`
  - 处理图片消息并生成结果卡片
  - 处理卡片按钮回调保存任务
- Vision 识别链路：
  - `POST /api/v1/vision/upload`
  - `GET /api/v1/vision/results/{traceId}`
  - `GET /api/v1/vision/traces`
  - `GET /api/v1/vision/files/{traceId}`
- OCR 接口统一为 `OcrResult` 格式。
- 后端只保留 OCR HTTP Provider 作为生产路径，不再保留运行时 Mock Provider 开关。
- 规则抽取已实现摘要、任务、链接、日报素材的基础提取。
- 任务保存与任务列表：
  - `POST /api/v1/tasks/from-trace/{traceId}`
  - `GET /api/v1/tasks?status=todo`
  - `PATCH /api/v1/tasks/{taskId}/status`
- Android 工作台 API：
  - `/api/v1/workbench/overview`
  - 最近识别
  - OCR 文本
  - OCR 框选证据
  - 任务保存
  - 任务看板
- P0 第一步异步识别状态机：
  - 上传接口创建 trace 后快速返回。
  - 后台线程执行 OCR 和结构化抽取。
  - 结果接口返回真实 `stage`、`progress`、`message`、`errorCode`。
  - 支持 `processing`、`done`、`error` 三类状态。
  - 飞书图片事件收到后立即创建 trace 并调度后台处理。
  - Web 工作台会自动轮询处理中任务。
- P0 第二步飞书处理中卡片阶段更新：
  - 飞书事件服务已重构为只负责事件校验和分发。
  - 新增 `FeishuImageProcessingJobService` 承接飞书图片后台任务。
  - 飞书客户端已支持发送卡片后返回 messageId。
  - 飞书客户端已支持更新已发送的卡片。
  - 处理中卡片支持 `received`、`resource_downloading`、`resource_downloaded`、`ocr`、`ocr_done`、`structuring`、`done`、`error` 阶段。
  - 结果卡和异常卡会优先替换原处理中卡片，更新失败时回退为发送新卡片。
- 简洁 Android 接入检测：
  - 后端新增 `GET /api/v1/setup/readiness`。
  - Android 首页新增“一键检测”。
  - 一键检测统一展示后端、OCR、机器人、飞书事件回调和下一步。

已验证：

- 后端单元测试通过：12 个测试。
- 后端本地启动检查通过。
- 当前开发后端运行在 `18080`。
- 当前公网调试地址：`https://faces-away-cameras-shall.trycloudflare.com`。
- 当前飞书测试机器人：
  - `botId`: `bot_20260517_c228717a9558`
  - 事件回调：`https://faces-away-cameras-shall.trycloudflare.com/feishu/events/bot_20260517_c228717a9558`
  - 卡片回调：`https://faces-away-cameras-shall.trycloudflare.com/feishu/card-callback/bot_20260517_c228717a9558`
  - 状态：App ID / App Secret 校验通过，等待飞书开放平台配置事件并发送 `/ping`。
  - 注意：不在文档中记录 App Secret。

### 1.2 OCR HTTP 服务

已完成：

- `ocr-service/` FastAPI 服务骨架。
- `/health` 健康检查。
- `/ocr` 图片 OCR 接口。
- PaddleOCR 引擎封装。
- PaddleOCR 输出到统一 `OcrResult` 的转换层。
- `OcrBlock`、`bbox`、`plainText`、`quality` 等字段规范。
- OCR 服务 README 和启动脚本：
  - `scripts/run-ocr-service.sh`
  - `scripts/test-ocr-service.sh`

已验证：

- OCR 服务单元测试通过：47 个测试。
- Python 编译检查通过。
- OCR 服务当前运行在 `9000`。
- `/`、`/health`、`/ocr` 已区分清楚：浏览器检查用 `/` 或 `/health`，图片识别由后端 multipart 调用 `/ocr`。

当前推荐模型：

- 第一版优先使用 PaddleOCR PP-OCRv5 mobile det/rec。
- CPU、本地开发、截图 OCR 优先用 mobile 版。
- 如果部署到服务器且有 GPU，再切 server 版提升精度。
- PaddleOCR-VL、复杂多模态版面模型放到后续版本，不作为 v1 主阻塞项。

### 1.3 Android App

已完成：

- Android Studio Java 项目骨架，当前为单 `MainActivity` 程序化 UI。
- 页面状态包括工作台、命令中心、任务、项目、知识库、论文、群总结、OCR 工具、飞书配置和设置。
- `HttpURLConnection` 后端 API 客户端。
- 后端地址和 Admin Token 保存到 SharedPreferences。
- 图片选择、上传、结果展示、任务保存的基础链路。
- 机器人注册、回调 Token 更新和 readiness 检查入口。
- 首页支持“一键检测”，用于确认后端、OCR、机器人和飞书事件回调是否就绪。
- WSL Android 构建脚本：`scripts/build-android-wsl.sh`。
- WSL 下 `assembleDebug` 已通过，产物为 `android/app/build/outputs/apk/debug/app-debug.apk`。

当前问题：

- Android Studio GUI 内的 Gradle Sync 仍需在本机 IDE 里确认。
- Android UI 仍是 v1 骨架，还没有完全落地 v2 前端交互说明书里的所有页面和状态。

### 1.4 前端 / Web 工作台

已完成：

- 已有静态交互稿：`shiliu_ai_detailed_interaction_prototype.html`。
- 不再提供后端 Web 工作台页面；工作台数据由 Android 调用 `/api/v1/workbench/overview`。

当前状态：

- 目前 Web 工作台是可用的最小工具界面，不是完整正式前端工程。
- 还没有独立 Vite/React/Vue 工程。
- 还没有完整实现安装向导、权限诊断、日报编辑器、字段映射、隐私中心等 v2 页面。

## 2. 现在是否可以使用

可以用于本地开发联调和技术验证，但还不能算正式软件版本。

当前可用范围：

- 可以启动后端。
- 可以启动 OCR HTTP 服务。
- 可以通过 Android 或 Web 上传图片。
- 可以得到 OCR + 规则抽取结果。
- 可以保存任务。
- 可以在 Web 工作台查看识别记录和任务。
- 可以用真实飞书配置做 `/ping`、图片消息、卡片保存任务的联调。
- 当前飞书联调已经完成应用凭证校验，下一步是在飞书开放平台填写事件回调 URL、Verification Token、权限并发布应用。

当前不可直接用于生产：

- 飞书公网回调、权限、真实企业安装流程还需要完整端到端验证。
- OCR 服务需要稳定部署和性能测试。
- 图片处理链路已改为应用内异步，后续软件化需要升级为可持久化队列。
- App Secret 仍是占位加密，需要替换为真实密钥管理。
- 图片和 OCR 文本保留策略还未自动化。
- Web 工作台还不是正式产品前端。
- Android App 还需要完成真机编译和 UI 验收。

## 3. 关键缺口

### 3.1 P0 缺口

必须补齐后，才能进入真实试用：

- 可持久化任务队列：
  - 当前已是应用内异步线程。
  - 软件化部署时需要升级为 Redis/数据库队列，避免服务重启丢任务。
- 飞书真实端到端验证：
  - 公网 HTTPS 回调地址。
  - 事件订阅。
  - 图片资源下载权限。
  - 卡片按钮回调。
- OCR 服务部署：
  - 本地或服务器固定端口。
  - 模型文件管理。
  - 错误返回标准化。
- 数据库生产化：
  - 从 H2 切到 PostgreSQL。
  - 保留 H2 仅用于本地开发。
- Secret 真实加密：
  - 替换 Base64 占位实现。
  - 不在日志输出敏感字段。
- 数据清理：
  - 原图保留周期。
  - OCR 原文保留周期。
  - 失败任务清理。

### 3.2 P1 缺口

进入可销售/可交付软件前需要补齐：

- 正式 Web 前端工程。
- 安装与授权向导。
- 权限诊断页。
- 数据保存策略页。
- 同步目标配置页。
- 多维表格字段映射。
- 日报草稿与编辑器。
- 识别详情页的完整证据链交互。
- 任务看板筛选、编辑、同步状态。
- 用户登录和基础权限体系。

### 3.3 P2 缺口

增强产品差异化：

- 表格截图专用识别流程。
- 白板照片专用识别流程。
- 二维码/链接截图识别流程。
- 设计稿截图识别流程。
- 重复任务检测。
- 敏感信息检测与脱敏。
- LLM 结构化抽取。
- 飞书任务、多维表格、云文档深度同步。

## 4. 下一步执行计划

### Step 1：后台异步任务与状态机

状态：已完成。

已完成：

- 将飞书图片处理和 Android/Web 上传识别改为后台任务。
- 接口快速返回 `traceId` 和 `processing`。
- 轮询结果时能看到 `stage`、`progress`、`message`。
- `VisionTraceEntity` 已增加 `stage`、`progress`、`statusMessage`、`errorCode`。
- 已增加 `visionTaskExecutor` 后台任务执行器。
- `VisionPipelineService` 已拆分 trace 创建、阶段更新、OCR、抽取、完成和失败标记。
- `VisionController` 上传接口已改为创建任务并立即返回。
- Feishu 图片事件已改为收到后立即返回，后台继续下载图片并处理。
- Web 工作台上传和详情页已支持自动轮询。

验收标准：

- 已通过：上传接口创建 trace 后立即返回 `processing`。
- 已通过：`/api/v1/vision/results/{traceId}` 能看到处理中进度。
- 已通过：OCR 服务慢或失败时，后端不会卡死请求。
- 已通过：单元测试覆盖成功、失败、处理中三种状态。

### Step 2：Feishu 处理中卡片更新

状态：已完成。

已完成：

- 收到图片后先发处理中卡片。
- 后端每个阶段更新卡片状态。
- 完成后更新或发送结果总览卡。
- 失败时发送异常处理卡。
- 保存处理卡片的 `messageId`。
- `FeishuCardService` 已增加阶段卡片构建。
- `FeishuClientAdapter` 已增加 `updateCard`。
- `FeishuEventService` 已重构为事件分发，图片后台任务移到 `FeishuImageProcessingJobService`。
- 卡片 `config` 已增加 `update_multi: true`，满足共享卡片更新要求。

验收标准：

- 已通过：群里发图片后会先发送处理中卡。
- 已通过：后台阶段变化会调用飞书卡片更新。
- 已通过：成功后用结果总览卡替换处理中卡。
- 已通过：失败后用异常卡替换处理中卡，更新失败则发送新卡。
- 已通过：单元测试覆盖飞书图片事件后台任务和最终卡片更新。

### Step 3：PostgreSQL 与 Docker Compose

状态：已完成。

目标：

- 软件化运行环境标准化。
- 一条命令启动后端、数据库、OCR 服务。

已完成：

- `docker-compose.yml`
- PostgreSQL 服务。
- OCR 服务容器。
- 后端环境变量配置。
- 开发环境 `.env.example`。
- 后端新增 PostgreSQL JDBC driver。
- 后端配置支持通过环境变量覆盖 datasource、Admin Token、公网地址、文件目录和 OCR endpoint。
- 后端与 OCR 服务均新增 Dockerfile 和 `.dockerignore`。
- PostgreSQL、OCR 服务、后端均配置健康检查。

验收标准：

- 已通过：`docker compose config` 配置校验通过。
- 待实机镜像构建验证：`docker compose up --build` 可以启动主要服务。
- 已完成配置：后端容器使用 PostgreSQL。
- 已完成配置：OCR 服务健康检查通过后后端再启动。
- 已通过：README 中有清晰启动步骤。

### Step 4：真实飞书端到端联调

目标：

- 用真实飞书企业自建应用跑通：
  - `/ping`
  - 图片识别
  - 结果卡片
  - 保存任务
  - Android/Web 任务列表可见

需要准备：

- 公网 HTTPS 地址。
- 飞书 App ID。
- App Secret。
- Verification Token。
- 事件订阅。
- 资源读取权限。
- 发送消息权限。
- 卡片交互回调。

验收标准：

- 群里 `/ping` 收到 `pong`。
- 群里发图并 @机器人后收到识别结果卡。
- 点击保存任务后，后端数据库出现任务。
- Web 工作台任务列表能看到飞书保存的任务。

### Step 5：正式 Web 前端工程

目标：

- 把现在的静态原型和最小工作台升级成正式前端。
- 优先覆盖 v2 说明书里的 P0/P1 页面。

建议技术路线：

- Vite + React + TypeScript。
- TanStack Query 管理 API 请求。
- Tailwind 或 CSS Modules 管理样式。
- 独立 `web/` 目录。
- 后端只提供 API 和静态部署入口。

优先页面：

- 工作台首页。
- 识别详情页。
- OCR 证据高亮。
- 任务确认页。
- 任务看板。
- 设置与权限诊断。

验收标准：

- 若后续恢复 Web 端，应以独立前端调用现有 API，不恢复后端内置 `/workbench/` 静态页。
- 页面能真实调用后端 API。
- 上传、识别、保存任务、查看任务全链路可用。

### Step 6：Android 真机完成验收

目标：

- 让 Android Studio 可以稳定打开、编译、真机运行。

需要处理：

- 已完成：WSL Gradle native-platform 问题，改用仓库内 `android/.gradle-user-home`。
- 已完成：WSL 构建固定使用完整 JDK 17，避免 Java 21 JRE 缺少 `jlink`。
- 已完成：WSL 构建自动检测 Windows Android SDK 并生成 `android/local.properties`。
- 已完成：Android 明文 HTTP 调试配置。
- 真机局域网 IP 配置。
- 图片选择 URI 兼容。
- 上传进度和错误提示。

验收标准：

- WSL `assembleDebug` 成功。
- Android Studio Gradle Sync 成功。
- 真机 Run 成功。
- 真机能连后端。
- 真机能上传图片。
- 真机能展示识别结果。
- 真机能保存任务并查看任务列表。

## 5. 软件化路线

### 5.1 本地开发版

目标用户：

- 开发者。
- 内部测试人员。

交付方式：

- `docker compose up`
- Android Studio 打开 `android/`
- Android Studio 打开并运行 Android App。

要求：

- README 可跟跑。
- 测试可执行。
- 配置文件清晰。

### 5.2 内测软件版

目标用户：

- 小团队真实试用。
- 一个飞书企业。
- 少量项目群。

交付方式：

- 云服务器部署。
- HTTPS 域名。
- PostgreSQL。
- 后端 + OCR 服务常驻运行。
- Android APK 内测包。
- Web 工作台。

要求：

- 飞书真实链路稳定。
- 错误可恢复。
- 日志可排查。
- 数据可备份。

### 5.3 企业可交付版

目标用户：

- 企业管理员。
- 项目组成员。

交付方式：

- SaaS 或私有化部署。
- 管理后台。
- 多租户。
- 正式权限模型。
- 飞书应用发布流程。

要求：

- 租户隔离。
- Secret/KMS 管理。
- 审计日志。
- 数据清理策略。
- 企业权限诊断。
- 飞书多维表格/任务/云文档同步。

## 6. 最近建议优先级

建议按以下顺序继续：

1. 真实飞书端到端联调。
2. 实机执行 `docker compose up --build`，确认镜像构建、PaddleOCR 依赖下载和容器健康检查耗时。
3. Android 真机编译问题修复与真机验收。
4. 正式 Web 前端工程。
5. 多维表格/飞书任务同步。
6. 日报编辑器。
7. LLM 结构化抽取。
8. 表格、白板、二维码专项 CV 流程。

## 7. 当前风险

- OCR 质量和速度会直接决定用户第一印象。
- 飞书权限配置复杂，需要做权限诊断页减少人工排查。
- 当前仍是应用内异步线程，服务重启可能丢处理中任务，后续需要持久化队列。
- H2 仅适合本机开发，标准软件化环境应使用 PostgreSQL。
- Android 已能在 WSL 构建 debug APK，下一步需要真机安装和运行验收。
- 目前前端只是最小工作台，还不能支撑完整产品交互。
