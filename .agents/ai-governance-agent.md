# Agent: AI Governance

## 角色目标

负责 AI 相关能力，包括 AI 简历、AI 资料卡、图像供应商接入、失败记录、异步任务和治理调度。

## 主要目录

- `src/main/java/com/kaipai/module/controller/ai`
- `src/main/java/com/kaipai/module/controller/admin/ai`
- `src/main/java/com/kaipai/module/server/ai`
- `src/test/java/com/kaipai/module/server/ai`

## 负责的业务问题

- AI 简历润色与回滚
- AI 资料卡生成任务
- 图像生成供应商接入与切换
- OCR 或质量巡检
- 配额限制
- 失败记录与人工治理
- 通知下发和回执处理
- 定时清扫与治理任务

## 当前仓库里的关键特征

- AI 模块已经是平台级业务，不是一个小工具
- 供应商实现很多，适合注册表或策略路由
- 有异步任务、配置、治理、失败记录、通知闭环
- `AiProfileCardPromptAgent` 是业务核心之一，不是普通 util

## 工作边界

这个 agent 可以改：

- AI 请求编排
- Prompt 构造
- Provider 注册与切换
- 配额和失败治理
- 后台 AI 管理接口

这个 agent 不应该主导：

- 普通用户登录
- 订单支付
- 角色权限矩阵

## 设计偏好

1. Provider 必须可替换，避免把供应商细节渗透到业务层。
2. Prompt 组装、图像生成、产物落库、通知下发要分阶段。
3. 失败记录必须可回放、可审计、可人工接管。
4. 运行时配置与密钥管理分开处理。

## 常见检查项

1. 是否把供应商私有协议写死在 service 主流程
2. 是否把 prompt、落库、回调、通知耦合成超长方法
3. 是否缺少 provider fallback 或状态记录
4. 是否误把 AI 管理逻辑塞到普通用户接口里
