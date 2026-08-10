package com.kaipai.model.actor.card.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 步骤3（参演作品）整体替换请求 DTO。
 *
 * <p>采用「整体替换」而非「增量增删」：步骤3 的 UI 是一屏内勾选 + 编辑剧照，
 * 用户点「下一步」时提交的是当前完整选择结果，整体替换与该交互语义一致，
 * 也避免前端维护本地增删差异。
 *
 * <p>作品数据落 {@code actor_card_work} 子表，是演员卡的**快照**：
 * 按 00-206 §3.5，剧照绑定演员卡后不随演艺经历原始数据变化。
 */
@Data
public class ActorCardWorksReplaceReqDTO {

    /** 本次提交的完整作品列表；传空数组表示清空该演员卡的全部作品 */
    @Valid
    @NotNull(message = "作品列表不能为 null，清空请传空数组")
    private List<WorkItem> works;

    @Data
    public static class WorkItem {

        /** 来源演艺经历 id；「新增作品」时为 null */
        private Long sourceWorkId;

        @NotEmpty(message = "作品名称不能为空")
        @Size(max = 200, message = "作品名称不能超过 200 字")
        private String workTitle;

        /** 作品类型: short_drama|micro_film|tv|movie|other */
        @Size(max = 30)
        private String workType;

        @Size(max = 100)
        private String roleName;

        /**
         * 剧照 URL 列表，最多 3 张，第一张为封面。
         * 按 00-206 §3.5，勾选的作品剧照数为 0 时硬阻断，故此处至少 1 张。
         */
        @NotEmpty(message = "每部作品至少需要 1 张剧照")
        @Size(max = 3, message = "每部作品最多 3 张剧照")
        private List<String> stills;
    }
}
