package com.kaipai.module.server.card.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.module.model.card.dto.ActorCardConfigRespDTO;
import com.kaipai.module.model.card.dto.ActorCardConfigSaveDTO;
import com.kaipai.module.model.card.entity.ActorCardConfig;

public interface ActorCardConfigService extends IService<ActorCardConfig> {

    ActorCardConfigRespDTO actorConfig(Long actorId, String sceneKey);

    ActorCardConfigRespDTO saveActorConfig(Long currentUserId, ActorCardConfigSaveDTO dto);

    ActorCardConfigRespDTO applyLuckyColor(Long currentUserId, String sceneKey, String luckyColor);
}
