package com.kaipai.service.card;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.card.dto.AdminShareCardGovernanceDetailDTO;
import com.kaipai.model.card.dto.AdminShareCardGovernanceItemDTO;
import com.kaipai.model.card.dto.AdminShareCardGovernanceQueryDTO;
import com.kaipai.model.card.dto.ActorMyShareCardItemDTO;
import com.kaipai.model.card.dto.CreateShareCardDTO;
import com.kaipai.model.card.entity.UserShareCard;

import java.util.List;

public interface UserShareCardService extends IService<UserShareCard> {

    UserShareCard findActiveCardById(Long shareCardId);

    List<UserShareCard> listOwnedCards(Long userId);

    ActorMyShareCardItemDTO createCard(Long currentUserId, CreateShareCardDTO dto);

    void archiveCard(Long currentUserId, Long shareCardId);

    PageResult<AdminShareCardGovernanceItemDTO> adminShareCardList(AdminShareCardGovernanceQueryDTO queryDTO);

    AdminShareCardGovernanceDetailDTO adminShareCardDetail(Long shareCardId);

}



