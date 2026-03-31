package com.kaipai.module.server.card.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.card.entity.TemplatePublishLog;
import com.kaipai.module.server.card.mapper.TemplatePublishLogMapper;
import com.kaipai.module.server.card.service.TemplatePublishLogService;
import org.springframework.stereotype.Service;

@Service
public class TemplatePublishLogServiceImpl extends ServiceImpl<TemplatePublishLogMapper, TemplatePublishLog> implements TemplatePublishLogService {

    @Override
    public void recordPublishLog(TemplatePublishLog log) {
        save(log);
    }
}
