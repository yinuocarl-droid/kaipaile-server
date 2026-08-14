package com.kaipai.service.actor;

import com.kaipai.model.actor.card.dto.ActorCardPublicRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardRespDTO;

/**
 * 已发布演员卡公开视图与复制创建（00-215 / 00-218）。
 */
public interface ActorCardPublicService {

    /**
     * 公开查看已发布演员卡（无需鉴权，观看者分享落地）。
     * 草稿返回 403「该演员卡尚未发布」，不存在返回 404「演员卡不存在」。
     */
    ActorCardPublicRespDTO getPublicView(Long cardId);

    /**
     * 复制已发布演员卡为新草稿（含参演作品子表），返回新草稿。
     */
    ActorCardRespDTO copy(Long userId, Long cardId);
}
