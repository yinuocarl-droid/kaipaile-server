package com.kaipai.module.server.card.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.card.dto.ActorSceneTemplateRespDTO;
import com.kaipai.module.model.card.dto.TemplateCreateDTO;
import com.kaipai.module.model.card.dto.TemplateDetailDTO;
import com.kaipai.module.model.card.dto.TemplateItemDTO;
import com.kaipai.module.model.card.dto.TemplateListQueryDTO;
import com.kaipai.module.model.card.dto.TemplatePublishDTO;
import com.kaipai.module.model.card.dto.TemplateRollbackDTO;
import com.kaipai.module.model.card.dto.TemplateSortDTO;
import com.kaipai.module.model.card.dto.TemplateStatusChangeDTO;
import com.kaipai.module.model.card.dto.TemplateUpdateDTO;
import com.kaipai.module.model.card.dto.ThemeTokenItemDTO;
import com.kaipai.module.model.card.dto.ThemeTokenQueryDTO;
import com.kaipai.module.model.card.dto.ThemeTokenUpdateDTO;
import com.kaipai.module.model.card.dto.ShareArtifactItemDTO;
import com.kaipai.module.model.card.dto.ShareArtifactQueryDTO;
import com.kaipai.module.model.card.dto.ShareArtifactUpdateDTO;
import com.kaipai.module.model.card.entity.CardSceneTemplate;

import java.util.List;

public interface CardSceneTemplateService extends IService<CardSceneTemplate> {

    List<ActorSceneTemplateRespDTO> actorSceneTemplates();

    String resolveSceneDisplayName(String templateSceneCode);

    PageResult<TemplateItemDTO> adminTemplateList(TemplateListQueryDTO dto);

    TemplateDetailDTO adminTemplateDetail(Long templateId);

    void createTemplate(TemplateCreateDTO dto);

    void updateTemplate(TemplateUpdateDTO dto);

    void enableTemplate(Long templateId, TemplateStatusChangeDTO dto);

    void disableTemplate(Long templateId, TemplateStatusChangeDTO dto);

    void sortTemplate(Long templateId, TemplateSortDTO dto);

    void publishTemplate(TemplatePublishDTO dto);

    void rollbackTemplate(TemplateRollbackDTO dto);

    PageResult<ThemeTokenItemDTO> adminThemeTokenList(ThemeTokenQueryDTO dto);

    void updateThemeToken(Long templateId, ThemeTokenUpdateDTO dto);

    PageResult<ShareArtifactItemDTO> adminShareArtifactList(ShareArtifactQueryDTO dto);

    void updateShareArtifact(Long templateId, ShareArtifactUpdateDTO dto);
}



