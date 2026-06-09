# Current private-chat demo runbook

This repo now keeps real runtime secrets out of tracked files. Local runs read `.env.local`; examples stay in `.env.example`.

## Short-term Feishu + phone flow

1. Start the backend/tunnel only when you are ready to test:

```bash
./scripts/run-remote-mobile-backend.sh
```

2. Copy the new Cloudflare Quick Tunnel URL printed by the script into Feishu Open Platform:

```text
事件订阅请求地址: https://<your-quick-tunnel>.trycloudflare.com/feishu/events/bot_20260528_dd2222539100
卡片回调请求地址: https://<your-quick-tunnel>.trycloudflare.com/feishu/card-callback/bot_20260528_dd2222539100
```

3. For a real Android phone APK, set the same tunnel URL before building:

```bash
rtk ./scripts/package-demo-phone-apk.sh https://<your-quick-tunnel>.trycloudflare.com
```

4. Install the debug APK from `android/app/build/outputs/apk/debug/app-debug.apk`.

The demo APK script refuses to build until `.env.local` contains the current `SHILIU_MOBILE_BACKEND_URL`, admin token, and bot config. This prevents accidentally installing an APK that still points to an old Cloudflare Quick Tunnel URL.

5. Private chat tests are the safest path for the defense demo. Group chat delivery still depends on Feishu group installation, mention delivery, and platform permissions.

## Runtime AI config

The local `.env.local` enables an OpenAI-compatible NewAPI endpoint and selects `gpt-4o-mini` by default. Do not commit `.env.local`.

---

# 视流 AI V1

视流 AI V1 是 Android-first 的移动端 AI 项目助理。Android 端提供命令中心、任务、项目、知识库、论文收集、群消息总结、CV/OCR 工具和飞书连接入口；后端提供统一 AgentRun API、任务保存、OCR 上传、飞书回调和 OpenAI 兼容模型调用。

## 当前能力

- Android 命令中心调用 `POST /api/v1/agent/runs`，按命令路由到 Codex/Research/Digest/Task/Memory 模块。
- `/plan` 和 `/bug` 会做真实本地项目只读扫描；`/paper` 会调用 arXiv 真实检索；飞书 `/digest` 会读取当前群最近文本消息。
- AgentRun 结果可保存为任务：`POST /api/v1/agent/runs/{runId}/tasks`。
- AgentRun 会持久化到数据库，重启后仍可通过 `GET /api/v1/agent/runs` 查看最近运行。
- 任务中心读取和创建真实后端任务：`GET/POST /api/v1/tasks`。
- 知识库支持真实读写：`GET/POST /api/v1/memory`。
- CV/OCR 工具上传图片：`POST /api/v1/vision/upload`，并轮询 `GET /api/v1/vision/results/{traceId}`。
- 飞书机器人回调走后端 `/feishu/events/{botId}` 和 `/feishu/card-callback/{botId}`；文本命令会复用 AgentRun。
- 设置页可保存后端地址、Admin Token 和项目路径。

Codex 真写文件没有从手机端直接开放；移动端默认只触发只读分析和任务生成，写代码仍需要人工确认。

访问路径和数据真实性规则见 `docs/rules.md`。后端会用 `SHILIU_AGENT_ALLOWED_PROJECT_ROOTS` 强制限制 AgentRun 可访问的项目路径；只改文档不会放开访问。

## Android 构建

用 Android Studio 打开 `android/` 目录，或在 WSL 下执行：

```bash
./scripts/build-android-wsl.sh
```

启动模拟器（可直接指定窗口位置）：

```bash
./scripts/launch-android-emulator-wsl.sh Medium_Phone_API_36.1
# 直接定位（x,y）:
EMULATOR_WINDOW_POS="0,700" ./scripts/launch-android-emulator-wsl.sh Medium_Phone_API_36.1
./scripts/launch-android-emulator-wsl.sh Medium_Phone_API_36.1 0,700
```

常用定位环境变量：

- `EMULATOR_WINDOW_POS="x,y"`：窗口左上角坐标（最高优先级，或放在第二个参数）。
- `EMULATOR_WINDOW_X` / `EMULATOR_WINDOW_Y`：分别指定 x、y（如 `EMULATOR_WINDOW_Y=700`）。
- `EMULATOR_WINDOW_POSITION=bottom`：自动放到底部（默认偏移 700，可通过 `EMULATOR_WINDOW_BOTTOM_OFFSET` 覆盖）。

构建产物：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

模拟器默认后端地址可用：

```text
http://10.0.2.2:8080
```

真机需要在设置页改成电脑局域网 IP，例如：

```text
http://192.168.1.23:8080
```

本地开发默认 Admin Token：

```text
dev-admin-token
```

## 远程手机访问本地后端

人在外面、后端跑在本地电脑时，推荐通过 Cloudflare Tunnel 暴露一个临时 HTTPS 地址：

```bash
export SHILIU_ADMIN_TOKEN="$(openssl rand -hex 24)"
./scripts/run-remote-mobile-backend.sh
```

脚本会输出 Android 设置页需要填写的：

```text
Backend URL : https://xxxx.trycloudflare.com
Admin Token : <你的强 token>
```

详细设计见：

```text
docs/remote-mobile-access.md
```

## 飞书用法

把机器人拉进客户项目群后，可以直接发送：

```text
@视流助手 /ping
@视流助手 添加一个任务：整理本周竞品资料
@视流助手 /plan 分析项目下一步
@视流助手 /paper 收集 benchmark 测评论文
@视流助手 /digest 总结今天群消息
@视流助手 /remember 记住：客户更关注移动端任务闭环
```

也可以直接发送图片、聊天截图或需求截图，机器人会进入 OCR 和任务提取流程。

## 后端运行

```bash
cd backend
./mvnw test
./mvnw spring-boot:run
```

健康检查：

```bash
curl http://localhost:8080/api/v1/health
```

需要鉴权的接口添加：

```bash
-H 'Authorization: Bearer dev-admin-token'
```

## OCR 服务

```bash
./scripts/run-ocr-service.sh
```

默认 OCR endpoint：

```text
http://localhost:9000/ocr
```

后端默认读取：

```text
SHILIU_OCR_HTTP_ENDPOINT=http://localhost:9000/ocr
```

## 大模型配置

后端使用 OpenAI 兼容 `/v1/chat/completions` 接口。通过环境变量配置：

```bash
export SHILIU_LLM_API_BASE_URL='https://your-provider.example'
export SHILIU_LLM_API_KEY='your-key'
export SHILIU_LLM_MODEL='gpt-4o-mini'
```

不要把真实 key 写入仓库。

## Agent 路径规则

默认只允许访问：

```text
/home/chase/GitHub/shiliu-ai-v1
```

需要增加可访问项目时，在启动后端前设置：

```bash
export SHILIU_AGENT_ALLOWED_PROJECT_ROOTS="/home/chase/GitHub/shiliu-ai-v1,/home/chase/GitHub/another-project"
```

## 关键接口

```http
GET  /api/v1/health
GET  /api/v1/setup/readiness
GET  /api/v1/workbench/overview
POST /api/v1/agent/runs
GET  /api/v1/agent/runs/{runId}
POST /api/v1/agent/runs/{runId}/tasks
GET  /api/v1/memory
POST /api/v1/memory
GET  /api/v1/tasks
POST /api/v1/tasks
PATCH /api/v1/tasks/{taskId}/status
POST /api/v1/vision/upload
GET  /api/v1/vision/results/{traceId}
POST /api/v1/bots/register
POST /feishu/events/{botId}
```
