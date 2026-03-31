package com.kaipai.module.server.card.service;

import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.card.dto.TemplatePublishLogItemDTO;
import com.kaipai.module.model.card.dto.TemplatePublishLogQueryDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.module.model.card.entity.TemplatePublishLog;

import java.util.List;

public interface TemplatePublishLogService extends IService<TemplatePublishLog> {

    void recordPublishLog(TemplatePublishLog log);

    List<TemplatePublishLogItemDTO> listByTemplateId(Long templateId);

    PageResult<TemplatePublishLogItemDTO> adminPublishLogList(TemplatePublishLogQueryDTO queryDTO);
}
