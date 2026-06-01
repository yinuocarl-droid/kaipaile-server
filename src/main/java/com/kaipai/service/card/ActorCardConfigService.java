package com.kaipai.service.card;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.model.card.dto.ActorCardConfigRespDTO;
import com.kaipai.model.card.dto.ActorMyShareCardsRespDTO;
import com.kaipai.model.card.dto.ActorCardConfigSaveDTO;
import com.kaipai.model.card.entity.ActorCardConfig;

public interface ActorCardConfigService extends IService<ActorCardConfig> {

    ActorCardConfigRespDTO actorConfig(Long shareCardId);

    ActorCardConfigRespDTO saveActorConfig(Long currentUserId, ActorCardConfigSaveDTO dto);

    ActorMyShareCardsRespDTO myCards(Long profileUserId);
}



