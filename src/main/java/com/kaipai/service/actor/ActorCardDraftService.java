package com.kaipai.service.actor;

import com.kaipai.model.actor.card.dto.ActorCardRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardStepSaveReqDTO;

import java.util.List;

public interface ActorCardDraftService {

    /** 新建草稿，返回演员卡基础信息（含 id） */
    ActorCardRespDTO createDraft(Long userId);

    /** 按步骤自动保存，只更新传入的非 null 字段 */
    void saveStep(Long userId, Long cardId, ActorCardStepSaveReqDTO dto);

    /** 读取单个草稿完整数据 */
    ActorCardRespDTO getDraft(Long userId, Long cardId);

    /** 查询当前用户的草稿列表（未删除、未发布） */
    List<ActorCardRespDTO> listDrafts(Long userId);

    /** 删除草稿（逻辑删除，仅允许 draft 状态） */
    void deleteDraft(Long userId, Long cardId);
}
