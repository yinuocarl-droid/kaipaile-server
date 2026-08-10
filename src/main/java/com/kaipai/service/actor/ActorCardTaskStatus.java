package com.kaipai.service.actor;

/**
 * 演员卡异步任务状态：持久层取值 → API 契约取值 的边界归一化。
 *
 * <p>持久层沿用 {@code success}（{@code actor_ai_profile_card_task} 已有存量行，
 * 且 {@code AiProfileCardServiceImpl} 多处以 {@code success} 作查询条件，改表会波及无关链路），
 * 而 API 契约是 {@code pending | running | done | failed} ——
 * 两个 RespDTO 的注释本就这么写，错的是实现从未兑现它。
 *
 * <p>归一化放在 DTO 出口而不是各客户端：否则每个客户端都得自己打一遍补丁，
 * 今后新增客户端还要重新踩一次。
 */
public final class ActorCardTaskStatus {

    /** 持久层成功态 */
    public static final String PERSISTED_SUCCESS = "success";

    /** API 契约成功态 */
    public static final String API_DONE = "done";

    private ActorCardTaskStatus() {
    }

    /** 持久层状态 → API 契约状态。仅 success 需要映射，其余三态两侧同名，原样透出。 */
    public static String toApi(String persistedStatus) {
        return PERSISTED_SUCCESS.equals(persistedStatus) ? API_DONE : persistedStatus;
    }
}
