package com.kaipai.module.server.crew.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.crew.entity.IntermediaryActor;
import com.kaipai.module.server.crew.mapper.IntermediaryActorMapper;
import com.kaipai.module.server.crew.service.IntermediaryActorService;
import org.springframework.stereotype.Service;

@Service
public class IntermediaryActorServiceImpl extends ServiceImpl<IntermediaryActorMapper, IntermediaryActor> implements IntermediaryActorService {
}
