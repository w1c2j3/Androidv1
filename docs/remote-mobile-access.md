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

## 长期方案

Quick Tunnel 适合测试。稳定使用建议换成：

- Cloudflare Named Tunnel + 自己域名
- Tailscale Funnel
- 服务器反向代理到家里机器

无论哪种方案，Android 只需要保存一个 HTTPS Backend URL。
