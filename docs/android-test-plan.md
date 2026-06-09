# Android 真机测试计划

## 本地同网络测试

1. 启动后端：

   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

2. 如需 OCR，启动 OCR 服务：

   ```bash
   ./scripts/run-ocr-service.sh
   ```

3. Android Studio 打开 `android/`，或构建 APK：

   ```bash
   ./scripts/build-android-wsl.sh
   ```

4. 真机设置页填写电脑局域网地址，不要填 `localhost`：

   ```text
   http://192.168.1.23:8080
   ```

5. Admin Token 本地默认：

   ```text
   dev-admin-token
   ```

## 远程测试

人在外面、后端跑在本地电脑时：

```bash
export SHILIU_ADMIN_TOKEN="$(openssl rand -hex 24)"
./scripts/run-remote-mobile-backend.sh
```

把脚本输出的 `Backend URL` 和 `Admin Token` 填到 Android 设置页。

## 功能用例

### 1. 连接检测

进入「设置」，填写后端地址和 Admin Token，点击「保存并检测」。

预期：工作台显示“后端已连接”，飞书页能显示 readiness 状态。

### 2. 命令中心

进入「命令中心」，输入：

```text
/plan 分析项目下一步
```

预期：创建 AgentRun，展示 summary、risks、next steps 和任务候选。

### 3. 保存任务

在运行结果页点击「保存为任务」。

预期：任务中心刷新后看到 `todo` 任务。

### 4. 知识库

进入「知识库」，保存一条产品决策。

预期：刷新后能从 `GET /api/v1/memory` 读回。

### 5. 论文收集

进入「论文收集」，点击开始收集。

预期：后端返回论文候选和阅读任务，保存后进入任务中心。

### 6. 群消息总结

进入「消息总结」，点击开始总结。

预期：返回摘要、风险、任务和下一步。

### 7. OCR

进入「工具 · CV/OCR」，选择图片并上传。

预期：Android 自动轮询结果，完成后进入截图分发页，可保存 OCR 任务候选。

## 常见问题

- 真机本地访问失败：确认电脑防火墙允许 8080，手机和电脑在同一网络。
- 远程访问失败：确认 Cloudflare Tunnel 没有退出，Android 设置页使用的是最新 HTTPS 地址。
- 401：Admin Token 不一致。
- OCR 一直 processing：确认 OCR 服务已启动，或查看运行日志。
- 公网测试不要使用默认 `dev-admin-token`。
