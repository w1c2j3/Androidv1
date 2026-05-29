# 运行规则

这份文档给人看；真正的访问限制由后端配置和代码执行。

## 项目路径

手机端、飞书机器人和 AgentRun 只能请求访问后端允许的项目根目录。

当前默认允许路径：

```text
/home/chase/GitHub/shiliu-ai-v1
```

如需增加路径，不要改 Android 端默认值，应该在启动后端前设置：

```bash
export SHILIU_AGENT_ALLOWED_PROJECT_ROOTS="/home/chase/GitHub/shiliu-ai-v1,/home/chase/GitHub/another-project"
```

规则：

- `projectPath` 必须是绝对路径。
- `projectPath` 必须等于某个允许根目录，或位于允许根目录内部。
- 不允许通过 `..` 跳出允许根目录。
- 未传 `projectPath` 时，后端使用第一个允许根目录。
- Markdown 里的路径只作为说明；以后真正增删路径必须同步到 `SHILIU_AGENT_ALLOWED_PROJECT_ROOTS` 或 `application.yml`。

## 数据真实性

- 没有真实数据源时，接口必须返回“未接入/未生成”，不能返回演示数据。
- 论文收集必须来自真实论文 API 或真实网页检索结果。
- 群消息总结必须来自真实飞书消息或用户提供的真实文本。
- 项目 review 必须来自真实文件扫描、Git 状态、测试输出或用户提供的上下文。
- 任务候选只能来自真实输入内容，不能用固定样例填充。

## 写入边界

- 移动端默认只允许触发只读分析和保存明确确认的任务/记忆。
- 修改代码、运行破坏性命令、清理数据和访问新项目路径，都必须有明确确认。
