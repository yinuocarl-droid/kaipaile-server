package com.kaipai.module.server.actor.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.actor.entity.ActorExperience;
import com.kaipai.module.server.actor.mapper.ActorExperienceMapper;
import com.kaipai.module.server.actor.service.ActorExperienceService;
import org.springframework.stereotype.Service;

@Service
public class ActorExperienceServiceImpl extends ServiceImpl<ActorExperienceMapper, ActorExperience> implements ActorExperienceService {
}
