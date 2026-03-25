package com.kaipai.module.server.company.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.company.entity.IntermediaryActor;
import com.kaipai.module.server.company.mapper.IntermediaryActorMapper;
import com.kaipai.module.server.company.service.IntermediaryActorService;
import org.springframework.stereotype.Service;

@Service
public class IntermediaryActorServiceImpl extends ServiceImpl<IntermediaryActorMapper, IntermediaryActor> implements IntermediaryActorService {
}
