package com.kaipai.module.server.card.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.card.dto.TemplateCreateDTO;
import com.kaipai.module.model.card.dto.TemplateItemDTO;
import com.kaipai.module.model.card.dto.TemplateListQueryDTO;
import com.kaipai.module.model.card.dto.TemplatePublishDTO;
import com.kaipai.module.model.card.dto.TemplateRollbackDTO;
import com.kaipai.module.model.card.dto.TemplateUpdateDTO;
import com.kaipai.module.model.card.entity.CardSceneTemplate;

public interface CardSceneTemplateService extends IService<CardSceneTemplate> {

    PageResult<TemplateItemDTO> adminTemplateList(TemplateListQueryDTO dto);

    void createTemplate(TemplateCreateDTO dto);

    void updateTemplate(TemplateUpdateDTO dto);

    void publishTemplate(TemplatePublishDTO dto);

    void rollbackTemplate(TemplateRollbackDTO dto);
}
