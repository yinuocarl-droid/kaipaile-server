package com.kaipai.service.actor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kaipai.mapper.actor.ActorCardBackgroundAdminMapper;
import com.kaipai.mapper.actor.ActorCardBackgroundMapper;
import com.kaipai.model.actor.card.dto.ActorCardBackgroundLibraryRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardTemplateCardRespDTO;
import com.kaipai.model.card.dto.ActorSceneTemplateRespDTO;
import com.kaipai.service.card.CardSceneTemplateService;
import com.kaipai.model.actor.card.entity.ActorCardBackground;
import com.kaipai.service.actor.support.ActorCardStyles;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActorCardBackgroundService {

    private final ActorCardBackgroundMapper backgroundMapper;
    private final AiGeneratedImageStorage imageStorage;
    private final ActorCardDecorationAssignmentService decorationAssignments;
    private final CardSceneTemplateService sceneTemplates;
    private final ActorCardBackgroundAdminMapper editorContexts;

    /**
     * 按风格加载背景图库，只返回 enabled=1 的图片，按 sort_order 排序。
     * 背景图不进用户素材库，只从此处读取。
     * 发版防御：表尚未预置（V20260814_002 未执行）时捕获 BadSqlGrammarException，
     * 返回空列表而非 500「操作失败」，避免首页模板区整体报错；预置后自动恢复。
     */
    public ActorCardBackgroundLibraryRespDTO listByStyle(String requestedStyle) {
        // 00-267: 风格 = 风格模板场景码；旧客户端的 ancient 与未迁移数据一并归到 costume
        String style = ActorCardStyles.normalize(requestedStyle);
        List<ActorCardBackground> items;
        if (style == null) {
            ActorCardBackgroundLibraryRespDTO empty = new ActorCardBackgroundLibraryRespDTO();
            empty.setImages(List.of());
            return empty;
        }
        try {
            items = backgroundMapper.selectList(
                    new LambdaQueryWrapper<ActorCardBackground>()
                            .in(ActorCardBackground::getStyle, ActorCardStyles.storedValues(style))
                            .eq(ActorCardBackground::getEnabled, 1)
                            .orderByAsc(ActorCardBackground::getSortOrder, ActorCardBackground::getId));
        } catch (org.springframework.dao.InvalidDataAccessResourceUsageException ex) {
            log.warn("[background-library] actor_card_background 表不可用（可能未执行 V20260814_002 预置迁移），返回空图库: {}", ex.getMessage());
            items = List.of();
        }

        ActorCardBackgroundLibraryRespDTO dto = new ActorCardBackgroundLibraryRespDTO();
        dto.setStyle(style);
        Map<Long, ActorCardDecorationAssignmentService.DecorationReadiness> readiness = items.isEmpty()
                ? Map.of()
                : decorationAssignments.backgroundReadiness(items.stream().map(ActorCardBackground::getId).toList());
        dto.setImages(items.stream().map(bg -> toItem(bg, readiness)).collect(Collectors.toList()));
        return dto;
    }

    /**
     * 00-267: 首页「模板创建」每个启用风格模板一张卡（按后台排序），封面 = 该模板「页面装修」里选定的编辑背景。
     * 背景分类与模板风格无关；停用背景或未选择视为未配置。编辑上下文表未预置时按未配置返回。
     */
    public List<ActorCardTemplateCardRespDTO> templateCards() {
        List<ActorSceneTemplateRespDTO> templates = sceneTemplates.actorSceneTemplates();
        Map<Long, ActorCardBackground> covers = new java.util.LinkedHashMap<>();
        for (ActorSceneTemplateRespDTO template : templates) {
            ActorCardBackground cover = editorBackground(template.getTemplateId());
            if (cover != null) covers.put(template.getTemplateId(), cover);
        }
        Map<Long, ActorCardDecorationAssignmentService.DecorationReadiness> readiness = covers.isEmpty()
                ? Map.of()
                : decorationAssignments.backgroundReadiness(covers.values().stream().map(ActorCardBackground::getId).distinct().toList());
        return templates.stream().map(template -> {
            ActorCardTemplateCardRespDTO card = new ActorCardTemplateCardRespDTO();
            card.setTemplateId(template.getTemplateId());
            card.setTemplateSceneCode(template.getTemplateSceneCode());
            card.setName(template.getName());
            ActorCardBackground cover = covers.get(template.getTemplateId());
            card.setBackground(cover == null ? null : toItem(cover, readiness));
            return card;
        }).collect(Collectors.toList());
    }

    private ActorCardBackground editorBackground(Long templateId) {
        try {
            var context = editorContexts.context(templateId);
            if (context == null || context.getBackgroundId() == null) return null;
            ActorCardBackground background = backgroundMapper.selectById(context.getBackgroundId());
            return background != null && Integer.valueOf(1).equals(background.getEnabled()) ? background : null;
        } catch (org.springframework.dao.DataAccessException ex) {
            log.warn("[template-cards] 模板 {} 编辑背景不可读，按未配置处理: {}", templateId, ex.getMessage());
            return null;
        }
    }

    private ActorCardBackgroundLibraryRespDTO.BackgroundItem toItem(
            ActorCardBackground bg, Map<Long, ActorCardDecorationAssignmentService.DecorationReadiness> readiness) {
        ActorCardBackgroundLibraryRespDTO.BackgroundItem item =
                new ActorCardBackgroundLibraryRespDTO.BackgroundItem();
        item.setId(bg.getId());
        item.setImageUrl(imageStorage.issueReadUrl(bg.getImageUrl(), Duration.ofMinutes(10)));
        item.setThumbnailUrl(bg.getThumbnailUrl() == null || bg.getThumbnailUrl().isBlank()
                ? item.getImageUrl() : bg.getThumbnailUrl().equals(bg.getImageUrl())
                ? item.getImageUrl() : imageStorage.issueReadUrl(bg.getThumbnailUrl(), Duration.ofMinutes(10)));
        item.setSortOrder(bg.getSortOrder());
        ActorCardDecorationAssignmentService.DecorationReadiness decoration = readiness.get(bg.getId());
        item.setDecorationReady(decoration != null && decoration.ready());
        item.setDecorationUnavailableReason(decoration == null
                ? "当前背景尚未绑定已发布装修版本" : decoration.unavailableReason());
        return item;
    }
}
