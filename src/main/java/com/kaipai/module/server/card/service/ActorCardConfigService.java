package com.kaipai.module.server.card.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.module.model.card.dto.ActorCardConfigRespDTO;
import com.kaipai.module.model.card.dto.ActorMyShareCardsRespDTO;
import com.kaipai.module.model.card.dto.ActorCardConfigSaveDTO;
import com.kaipai.module.model.card.entity.ActorCardConfig;

public interface ActorCardConfigService extends IService<ActorCardConfig> {

    ActorCardConfigRespDTO actorConfig(Long shareCardId);

    ActorCardConfigRespDTO saveActorConfig(Long currentUserId, ActorCardConfigSaveDTO dto);

    ActorMyShareCardsRespDTO myCards(Long profileUserId);
}



