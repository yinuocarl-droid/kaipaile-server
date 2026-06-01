package com.kaipai.service.actor.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.model.actor.entity.ActorExperience;
import com.kaipai.mapper.actor.ActorExperienceMapper;
import com.kaipai.service.actor.ActorExperienceService;
import org.springframework.stereotype.Service;

@Service
public class ActorExperienceServiceImpl extends ServiceImpl<ActorExperienceMapper, ActorExperience> implements ActorExperienceService {
}
