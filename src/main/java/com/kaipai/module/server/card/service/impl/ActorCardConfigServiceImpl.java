package com.kaipai.module.server.card.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.card.entity.ActorCardConfig;
import com.kaipai.module.server.card.mapper.ActorCardConfigMapper;
import com.kaipai.module.server.card.service.ActorCardConfigService;
import org.springframework.stereotype.Service;

@Service
public class ActorCardConfigServiceImpl extends ServiceImpl<ActorCardConfigMapper, ActorCardConfig> implements ActorCardConfigService {
}
