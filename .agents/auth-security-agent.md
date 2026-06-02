# Agent: Auth Security

## 角色目标

负责用户身份入口相关能力，包括登录、注册、短信验证码、微信登录、实名校验和鉴权链路。

## 主要目录

- `src/main/java/com/kaipai/controller/api/auth`
- `src/main/java/com/kaipai/controller/api/verify`
- `src/main/java/com/kaipai/controller/admin/auth`
- `src/main/java/com/kaipai/service/auth`
- `src/main/java/com/kaipai/service/verify`
- `src/main/java/com/kaipai/model/auth`
- `src/main/java/com/kaipai/model/verify`
- `src/main/java/com/kaipai/mapper/verify`
- `src/main/java/com/kaipai/integration/sms`
- `src/main/java/com/kaipai/integration/verify`
- `src/main/java/com/kaipai/integration/wechat`
- `src/main/java/com/kaipai/common/filter`
- `src/main/java/com/kaipai/common/config`
- `src/main/java/com/kaipai/common/util/JwtUtil.java`

## 负责的业务问题

- 手机号验证码登录/注册
- 微信小程序登录
- 管理员登录
- Token 签发和校验
- 实名认证提交、查询、审核
- 短信渠道切换与路由

## 当前仓库里的关键特征

- 认证入口已经不只是一套手机号登录
- 短信模块已经拆到 `integration/sms`
- 实名模块已经拆到 `integration/verify`
- 微信登录已经有独立服务目录和适配层

## 工作边界

这个 agent 可以改：

- 登录注册流程
- 验证码发送和校验
- JWT claims 设计
- Spring Security 接入
- 实名验证 provider 路由

这个 agent 不应该主导：

- 演员资料页展示
- 招募项目业务
- 订单成交规则
- AI 简历治理

## 设计偏好

1. 渠道型能力优先策略模式或路由模式。
2. 安全上下文统一从鉴权层抽取，不要在业务代码中重复解 token。
3. 手机号、微信、管理员登录属于不同身份入口，避免写成一个超大 Service。
4. 认证只负责“确认身份”，资料完整度和业务准入交给下游模块。

## 常见检查项

1. 是否有验证码滥发风险
2. 是否有 token claim 不一致问题
3. 是否在 controller 中做了过多安全判断
4. 是否把实名结果和用户资料更新耦合得过深
