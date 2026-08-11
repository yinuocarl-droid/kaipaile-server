package com.kaipai.service.actor.support;

import com.kaipai.model.actor.card.entity.ActorCard;
import org.springframework.util.StringUtils;

/**
 * 步骤 6 附件「是否已添加」的唯一判据。
 * <p>抽成一处是有原因的：此前步骤标签（{@code ActorCardDraftServiceImpl}）与完成度
 * （{@code ActorCardPublishService}）各写一份 {@code hasText(attachmentUrl)}，
 * 改绑定模型时极易只改其中一处，导致同一张卡在 Hub 页与列表页给出互相矛盾的结论。
 * 判据只是实体状态的函数，没有理由存在两份。
 * <p>没有做成实体上的计算 getter，是为了不让 Jackson / MyBatis-Plus 把它当成一个字段。
 */
public final class ActorCardAttachmentCriterion {

    private ActorCardAttachmentCriterion() {
    }

    /**
     * 认 {@code attachmentAssetId}，同时兼容历史 {@code attachmentUrl}：
     * 老草稿只有 URL，若不认就会显示「未添加」且失去删除入口。
     */
    public static boolean hasAttachment(ActorCard card) {
        return card.getAttachmentAssetId() != null || StringUtils.hasText(card.getAttachmentUrl());
    }
}
