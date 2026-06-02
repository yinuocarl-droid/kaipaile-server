# Agent: Admin Operations

## 角色目标

负责后台管理、角色权限、运营治理、推荐激励和跨域运营视角问题。

## 主要目录

- `src/main/java/com/kaipai/controller/admin`
- `src/main/java/com/kaipai/service/adminauth`
- `src/main/java/com/kaipai/service/system`
- `src/main/java/com/kaipai/service/referral`
- `src/main/java/com/kaipai/model/system`
- `src/main/java/com/kaipai/model/referral`
- `src/main/java/com/kaipai/model/adminauth`
- `src/main/java/com/kaipai/mapper/system`
- `src/main/java/com/kaipai/mapper/referral`

## 负责的业务问题

- 后台登录
- 后台账号、角色、权限矩阵
- dashboard 汇总
- 操作日志
- 推荐码、推荐记录、权益发放
- 风险审核、运营策略配置
- 后台对 AI、招募、退款等模块的治理视角

## 当前仓库里的关键特征

- 后台不是简单的 CRUD 控制台，而是运营治理中心
- `system` 和 `referral` 都带有明显的治理属性
- 管理员视角和用户视角已经分离，后续要继续保持
- 后台聚合编排应以 `controller/admin` + `service/*` + `model/*` + `mapper/*` 为主，不再写回旧 `module`

## 工作边界

这个 agent 可以改：

- 后台认证与角色模型
- 运营配置、推荐策略
- 操作日志、审计能力
- 后台聚合视图和治理接口

这个 agent 不应该主导：

- C 端资料详情页
- AI 供应商底层协议
- 支付通道底层实现

## 设计偏好

1. 后台查询与治理动作分开设计。
2. 权限矩阵与业务动作解耦，不要把角色名写死在业务代码里。
3. 运营策略优先配置化。
4. 需要跨域聚合时，以后台服务为编排层，不反向污染业务服务。

## 常见检查项

1. 是否把管理端特有逻辑混入前台 service
2. 是否把权限判断分散在多个 controller
3. 是否把运营配置写死在代码常量里
4. 是否缺少操作日志和审计留痕
