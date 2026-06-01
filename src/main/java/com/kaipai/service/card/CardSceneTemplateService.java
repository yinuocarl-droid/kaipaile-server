package com.kaipai.service.card;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.card.dto.ActorSceneTemplateRespDTO;
import com.kaipai.model.card.dto.TemplateCreateDTO;
import com.kaipai.model.card.dto.TemplateDetailDTO;
import com.kaipai.model.card.dto.TemplateItemDTO;
import com.kaipai.model.card.dto.TemplateListQueryDTO;
import com.kaipai.model.card.dto.TemplatePublishDTO;
import com.kaipai.model.card.dto.TemplateRollbackDTO;
import com.kaipai.model.card.dto.TemplateSortDTO;
import com.kaipai.model.card.dto.TemplateStatusChangeDTO;
import com.kaipai.model.card.dto.TemplateUpdateDTO;
import com.kaipai.model.card.dto.ThemeTokenItemDTO;
import com.kaipai.model.card.dto.ThemeTokenQueryDTO;
import com.kaipai.model.card.dto.ThemeTokenUpdateDTO;
import com.kaipai.model.card.dto.ShareArtifactItemDTO;
import com.kaipai.model.card.dto.ShareArtifactQueryDTO;
import com.kaipai.model.card.dto.ShareArtifactUpdateDTO;
import com.kaipai.model.card.entity.CardSceneTemplate;

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



