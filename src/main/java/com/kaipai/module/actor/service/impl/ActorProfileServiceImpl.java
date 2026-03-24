package com.kaipai.module.actor.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.actor.entity.ActorProfile;
import com.kaipai.module.actor.mapper.ActorProfileMapper;
import com.kaipai.module.actor.service.ActorProfileService;
import org.springframework.stereotype.Service;

@Service
public class ActorProfileServiceImpl extends ServiceImpl<ActorProfileMapper, ActorProfile> implements ActorProfileService {
}
