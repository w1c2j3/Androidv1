# 远程手机访问本地后端

目标场景：后端、OCR、项目文件都跑在本地电脑；人在外面用 Android 手机访问同一套能力。

## 推荐链路

```text
Android App
  -> HTTPS 公网地址
  -> Cloudflare Tunnel
  -> localhost:8080 后端
  -> 本地项目 / OCR / 飞书 / LLM
```

这样手机不需要和电脑在同一个局域网，也不需要把路由器端口暴露到公网。

## 启动

先设置强 Admin Token。不要用默认 `dev-admin-token` 暴露公网。

```bash
export SHILIU_ADMIN_TOKEN="$(openssl rand -hex 24)"
./scripts/run-remote-mobile-backend.sh
```

脚本会输出：

```text
Backend URL : https://xxxx.trycloudflare.com
Admin Token : <你的 token>
```

手机 Android 设置页填写这两个值，然后点击“保存并检测”。

设置页会同时读取：

```http
GET /api/v1/setup/queues
```

用于显示 OCR/Vision 队列和飞书事件队列的真实 `active`、`queue`、`completed` 数字。大会演示时，如果看到 `busy`，说明后台正在排队；如果看到 `overloaded`，系统会进入演示保护，不会假装成功。

## Feishu

如果要让飞书事件也打到本地后端，把飞书应用后台的事件订阅地址设置成：

```text
https://xxxx.trycloudflare.com/feishu/events/{botId}
```

卡片回调地址：

```text
https://xxxx.trycloudflare.com/feishu/card-callback/{botId}
```

`{botId}` 来自 `POST /api/v1/bots/register` 返回值。

配置完成后，在客户群里可以测试：

```text
@视流助手 /ping
@视流助手 /plan 分析项目下一步
@视流助手 /paper 收集 benchmark 测评论文
@视流助手 添加一个任务：整理客户第一次试点反馈
```

## 安全边界

- 公网访问必须使用强 `SHILIU_ADMIN_TOKEN`。
- 不要把 `SHILIU_LLM_API_KEY`、飞书 App Secret、Admin Token 写入仓库。
- 手机端 Codex 仍默认只读分析；写代码需要人工确认。
- Cloudflare Quick Tunnel 地址可能变化，重启脚本后需要在 Android 设置页更新后端地址。
- 演示前先上传一张图片预热 OCR；首次 PaddleOCR 加载最慢。
- 不要现场重启 Cloudflare Quick Tunnel，重启后飞书回调 URL 会变化。

## 演示保护

当前后端的演示保护策略：

- Android 上传图片立即返回 trace，后台 OCR 排队处理。
- 飞书文本事件立即返回，后台执行 AgentRun 后再回复。
- 飞书图片事件先创建 trace，再后台下载图片、OCR、更新卡片。
- 如果后台队列满，返回明确的保护提示，而不是卡死。
- 如果 LLM、飞书 OpenAPI 或 OCR HTTP 调用超时，返回可解释的降级文案或 trace 错误。

## 长期方案

Quick Tunnel 适合测试。稳定使用建议换成：

- Cloudflare Named Tunnel + 自己域名
- Tailscale Funnel
- 服务器反向代理到家里机器

无论哪种方案，Android 只需要保存一个 HTTPS Backend URL。
