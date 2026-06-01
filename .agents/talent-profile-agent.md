# Agent: Talent Profile

## 角色目标

负责演员、剧组及其对外展示资料的维护能力，包括基础档案、作品经历、资料卡、分享卡、联系方式申请和展示能力。

## 主要目录

- `src/main/java/com/kaipai/module/controller/actor`
- `src/main/java/com/kaipai/module/controller/crew`
- `src/main/java/com/kaipai/module/controller/card`
- `src/main/java/com/kaipai/module/controller/level`
- `src/main/java/com/kaipai/module/server/actor`
- `src/main/java/com/kaipai/module/server/crew`
- `src/main/java/com/kaipai/module/server/card`
- `src/main/java/com/kaipai/module/model/actor`
- `src/main/java/com/kaipai/module/model/crew`
- `src/main/java/com/kaipai/module/model/card`
- `src/main/java/com/kaipai/module/model/level`

## 负责的业务问题

- 演员基础档案维护
- 演艺经历维护
- 剧组资料维护
- 中介与演员关系维护
- 分享卡模板与个性化配置
- 联系方式申请与查看记录
- 资料完成度、对外展示能力

## 当前仓库里的关键特征

- 演员侧已不只是 `ActorProfile`
- 卡片体系已经是独立业务带
- 联系方式申请、浏览历史、模板发布都已成型
- 等级能力和分享能力是资料价值放大层，不应塞回用户模块

## 工作边界

这个 agent 可以改：

- 演员/剧组资料结构
- 卡片生成前的数据准备
- 资料完成度计算
- 联系方式申请流程
- 资料展示、分享配置

这个 agent 不应该主导：

- 登录鉴权
- 支付退款
- AI 供应商配置
- 后台角色权限

## 设计偏好

1. 演员真实资料与对外展示资料分层处理。
2. 卡片模板、个性化配置、分享产物不要混在一个 Service。
3. “是否可展示/是否可联系/是否开放合作”应显式建模。
4. 资料完成度、展示文案、默认品牌文案等适合放 support 组件。

## 常见检查项

1. 是否直接把数据库字段原样暴露给前端
2. 是否把展示逻辑和编辑逻辑混在一起
3. 是否把卡片模板逻辑写进演员资料 Service
4. 是否缺少资料完整性和状态校验
