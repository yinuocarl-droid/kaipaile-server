package com.kaipai.module.server.card.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.card.dto.AdminShareCardGovernanceDetailDTO;
import com.kaipai.module.model.card.dto.AdminShareCardGovernanceItemDTO;
import com.kaipai.module.model.card.dto.AdminShareCardGovernanceQueryDTO;
import com.kaipai.module.model.card.dto.ActorMyShareCardItemDTO;
import com.kaipai.module.model.card.dto.CreateShareCardDTO;
import com.kaipai.module.model.card.entity.UserShareCard;

import java.util.List;

public interface UserShareCardService extends IService<UserShareCard> {

    UserShareCard findActiveCardById(Long shareCardId);

    List<UserShareCard> listOwnedCards(Long userId);

    ActorMyShareCardItemDTO createCard(Long currentUserId, CreateShareCardDTO dto);

    void archiveCard(Long currentUserId, Long shareCardId);

    PageResult<AdminShareCardGovernanceItemDTO> adminShareCardList(AdminShareCardGovernanceQueryDTO queryDTO);

    AdminShareCardGovernanceDetailDTO adminShareCardDetail(Long shareCardId);

}



