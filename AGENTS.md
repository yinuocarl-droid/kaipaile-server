# Kaipai Backend Agents

本仓库使用 `agents` 文件来约束 AI 在不同业务域内的工作边界，避免在复杂模块中无序修改。

## 使用原则

1. 先判断需求属于哪个业务域，再阅读对应 agent 文件。
2. 涉及跨域改动时，先阅读 `project-architect`，再阅读具体领域 agent。
3. 涉及公共规范、命名、分层、DTO/Entity/Service 约束时，额外阅读 `backend-conventions`。
4. 如果需求同时触达多个模块，优先保证边界清晰，不把逻辑继续堆进 Controller。

## 当前包结构

- `src/main/java/com/kaipai/controller/admin`：后台管理端接口。
- `src/main/java/com/kaipai/controller/api`：小程序 / C 端 API 接口。
- `src/main/java/com/kaipai/service`：业务规则、事务和领域编排。
- `src/main/java/com/kaipai/model`：DTO、Entity、查询对象和响应对象。
- `src/main/java/com/kaipai/mapper`：MyBatis Mapper 接口。
- `src/main/java/com/kaipai/integration`：短信、实名、微信、AI、COS 等外部能力适配。
- `src/main/java/com/kaipai/common`：公共配置、异常、过滤器和通用工具。

## Agent 路由

- 项目级架构与模块边界：`.agents/project-architect.md`
- 后端编码与分层规范：`.agents/backend-conventions.md`
- C 端认证、登录、实名、微信、短信：`.agents/auth-security-agent.md`
- 演员、剧组、资料卡、分享卡：`.agents/talent-profile-agent.md`
- 招募、报名、订单、支付、退款：`.agents/recruit-transaction-agent.md`
- AI 简历、AI 资料卡、供应商配置、治理任务：`.agents/ai-governance-agent.md`
- 后台管理、角色权限、运营治理、推荐体系：`.agents/admin-operations-agent.md`

## 当前仓库理解

项目并不是单纯的 CRUD 后端，而是一个围绕演员供给、剧组招募、AI 增值能力、后台治理组成的业务平台。

当前主要业务带包括：

- 用户身份与登录认证
- 演员/剧组资料体系
- 招募与撮合交易
- AI 简历与 AI 资料卡
- 后台治理与推荐激励

这些业务带之间存在明显边界，不建议再按“谁顺手就写哪”继续扩散。
