# 第一版实现状态

## 已实现

1. Spring Boot 后端骨架、Maven wrapper、本地 H2 配置。
2. `GET /api/v1/health`。
3. 机器人注册和状态查询，`appSecret` 通过 `SecretEncryptor` 做 Base64 占位封装，注册和健康检查会真实校验飞书 tenant token。
4. 图片上传、文件落盘、HTTP OCR 接入、场景分类、规则抽取、识别结果查询。
5. 从 trace 保存任务、任务列表查询。
6. 飞书事件回调：challenge、`/ping`、图片事件链路。
7. 飞书卡片回调：`save_tasks` 保存任务。
8. Android Studio Java App：命令中心、AgentRun、任务、知识库、论文收集、群总结、图片上传、结果轮询和飞书连接状态。
9. 真实飞书 OpenAPI adapter：发送文本、发送卡片、下载图片资源。
10. HTTP OCR Provider：调用外部 OCR 服务并转换成统一 `OcrResult`。
11. OCR HTTP 服务：FastAPI + PaddleOCR 服务骨架、PP-OCRv5 mobile/server 配置、45 个纯单元测试覆盖输出转换和 HTTP 参数处理。
12. P0 飞书卡片：处理中卡、结果总览卡、任务确认卡、保存成功卡、异常卡。
13. P0 异步识别状态机：上传和飞书图片事件创建 trace 后立即返回，后台执行 OCR/抽取，结果接口返回真实 `stage`、`progress`、`message`、`errorCode`。
14. P0 飞书处理中卡片阶段更新：发送处理中卡后保存 messageId，后台阶段变化时更新卡片，完成时用结果总览卡替换处理中卡。
15. PostgreSQL + Docker Compose：新增标准本地软件化运行环境，包含 PostgreSQL、OCR 服务、后端容器、健康检查和 `.env.example`。
16. AgentRun 持久化：统一命令入口、最近运行查询、任务候选保存。
17. 知识库持久化：Android 和 `/remember` 命令都可写入长期记忆。
18. 远程手机访问：`scripts/run-remote-mobile-backend.sh` 通过 Cloudflare Tunnel 暴露本地后端 HTTPS 地址。

## 关键边界

- OCR 通过 `OcrProvider` 抽象，生产实现为 `HttpOcrProvider`，外部服务位于 `ocr-service/`。
- 飞书发送通过 `FeishuClientAdapter` 抽象，生产实现为 `RealFeishuClientAdapter`。
- 飞书资源下载通过 `FeishuResourceDownloader` 抽象，生产实现为 `RealFeishuResourceDownloader`。
- Vision pipeline 已改为后台执行，当前使用 Spring `TaskExecutor`，后续软件化部署时可升级为 Redis/数据库队列。
- 飞书图片事件处理已从 `FeishuEventService` 拆到 `FeishuImageProcessingJobService`，事件服务只保留校验、解析和分发职责。

## 下一步

1. 用远程 HTTPS 地址在外网手机上做端到端测试。
2. 用真实飞书应用凭证和公网 HTTPS 回调做端到端联调。
3. 实机执行 `docker compose up --build`，确认镜像构建、PaddleOCR 依赖下载和容器健康检查耗时。
4. 在真实图片集上调参 PP-OCRv5 mobile/server，并决定是否增加表格结构识别。
5. 增加日报编辑器、多维表格字段映射、trace 清理和图片保留策略。
