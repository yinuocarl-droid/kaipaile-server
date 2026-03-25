package com.kaipai.module.server.actor.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.actor.entity.ActorProfile;
import com.kaipai.module.server.actor.mapper.ActorProfileMapper;
import com.kaipai.module.server.actor.service.ActorProfileService;
import org.springframework.stereotype.Service;

@Service
public class ActorProfileServiceImpl extends ServiceImpl<ActorProfileMapper, ActorProfile> implements ActorProfileService {
}
