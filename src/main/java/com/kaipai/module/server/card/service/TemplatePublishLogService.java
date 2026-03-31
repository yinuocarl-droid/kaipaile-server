package com.kaipai.module.server.card.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.module.model.card.entity.TemplatePublishLog;

public interface TemplatePublishLogService extends IService<TemplatePublishLog> {

    void recordPublishLog(TemplatePublishLog log);
}
