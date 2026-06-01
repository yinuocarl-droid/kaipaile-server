# Agent: Recruit Transaction

## 角色目标

负责撮合与成交链路，包括招募项目、角色、报名、订单、支付和退款。

## 主要目录

- `src/main/java/com/kaipai/module/controller/recruit`
- `src/main/java/com/kaipai/module/controller/order`
- `src/main/java/com/kaipai/module/controller/payment`
- `src/main/java/com/kaipai/module/controller/refund`
- `src/main/java/com/kaipai/module/server/recruit`
- `src/main/java/com/kaipai/module/server/order`
- `src/main/java/com/kaipai/module/server/payment`
- `src/main/java/com/kaipai/module/server/refund`
- `src/main/java/com/kaipai/module/model/recruit`
- `src/main/java/com/kaipai/module/model/order`
- `src/main/java/com/kaipai/module/model/payment`
- `src/main/java/com/kaipai/module/model/refund`

## 负责的业务问题

- 招募项目发布与查询
- 角色维护
- 演员报名与处理
- 报名状态流转
- 成交订单建立与确认
- 支付订单与支付流水
- 退款申请、审核、日志

## 当前仓库里的关键特征

- 这一层已经从“简单招募帖子”演化成“项目 + 角色 + 报名 + 订单 + 退款”的完整链路
- 管理端也有对应治理入口
- 很适合按交易状态机来治理，而不是继续散写状态码判断

## 工作边界

这个 agent 可以改：

- 项目、角色、报名相关 API 和 Service
- 订单状态流转
- 支付与退款链路
- 前后台的招募治理逻辑

这个 agent 不应该主导：

- 纯资料展示逻辑
- AI 图像供应商接入
- 账号登录认证

## 设计偏好

1. 项目、角色、报名、订单分成清晰子阶段。
2. 状态流转优先集中治理，不要散在多个 controller/service 中。
3. 支付和退款必须保留可审计日志。
4. 前台用户操作与后台治理操作尽量分别定义 command / DTO。

## 常见检查项

1. 是否存在重复状态校验
2. 是否直接跨多个模块 update 状态而无统一事务边界
3. 是否把支付、退款判断写到招募 controller
4. 是否缺少管理端与用户端职责隔离
