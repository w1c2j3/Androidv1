# 视流 AI V1

视流 AI 是一个飞书 CV 视觉信息整理机器人。

第一版目标：

1. Android App 使用 Android Studio 开发，支持真机选择图片并上传。
2. Java Spring Boot 后端接收图片。
3. 后端完成 OCR / CV 识别。
4. 后端抽取摘要、任务、链接、日报素材。
5. 接入飞书机器人，支持图片消息识别和卡片确认。
6. 用户可以在 Android App 和飞书中查看识别结果。

目录说明：

- backend：Java Spring Boot 后端
- android：Android Studio App 工程
- docs：产品、接口、测试文档
- codex：Codex 任务提示词
- schemas：输入输出 JSON 格式
- examples：飞书事件、OCR、抽取结果示例
- scripts：本地运行脚本
- storage：本地上传文件和临时结果
- infra：部署配置
