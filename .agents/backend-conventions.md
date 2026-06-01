# Agent: Backend Conventions

## 角色目标

统一这个项目的后端分层、命名、DTO 设计、状态建模和公共编码习惯，减少“同类问题每个模块写法都不同”。

## 当前项目约束

- Spring Boot + MyBatis-Plus + Redis + MySQL
- 业务目录主要在 `src/main/java/com/kaipai/module`
- 公共基础设施在 `src/main/java/com/kaipai/common`
- 项目已有 `.cursor/rules.md`，其中要求：
  - 使用 Java 8+
  - 优先使用 Stream / Optional 提升可读性
  - 禁止使用过时 API
  - 日期优先 `LocalDateTime`
  - 类和 public 方法补 Javadoc

## 推荐分层职责

### Controller

负责：

- 接收参数
- 参数校验
- 调用应用服务
- 返回统一响应

不要负责：

- 复杂业务分支
- 事务编排
- Mapper 拼装

### Service

负责：

- 业务规则
- 状态流转
- 事务边界
- 跨表协调

### Mapper

负责：

- 持久化查询
- 列表筛选
- 聚合视图查询

不要负责：

- 业务状态判断
- 领域规则

### Support / Provider / Adapter

适合承载：

- 第三方集成
- 文案生成
- 规则计算
- 路由分发
- 可替换实现

## DTO 规则

1. `SaveDTO` 用于新增/编辑。
2. `QueryDTO` 用于分页和筛选。
3. `RespDTO` / `DetailDTO` / `ItemDTO` 用于响应输出。
4. 不要把 Entity 直接返回给前端。
5. DTO 字段名要面向接口语义，不要机械复制数据库列名。

## Entity 规则

1. Entity 只表达存储模型，不承载控制层语义。
2. 枚举型状态优先沉淀为常量或枚举，不要在业务里散落魔法值。
3. `extendedField` 仅用于兼容过渡，不应成为长期功能扩展主入口。

## Service 设计规则

1. 一个 Service 只聚焦一个明确业务域。
2. 跨域编排由更高一层应用服务负责，不要相互深度循环注入。
3. 能抽成策略的第三方实现，不要写成超长 `if-else`。
4. 能沉淀为查询对象和响应对象的，不要把 Map 作为通用返回。

## 常见重构偏好

- 把重复的状态校验提炼成私有方法或 support 组件
- 把第三方渠道接入做成 `Provider` / `Sender` / `Registry`
- 把复杂组装逻辑移出 Controller，进入 service 或 assembler
- 把 AI、支付、短信、实名等外部能力做成路由 + 实现类

## 提交改动前的自检

1. 这段逻辑是否放错层了
2. 是否直接返回了 Entity
3. 是否引入新的魔法值
4. 是否让一个 Service 同时承担了两个业务域
5. 是否可以抽成 support / provider / strategy
