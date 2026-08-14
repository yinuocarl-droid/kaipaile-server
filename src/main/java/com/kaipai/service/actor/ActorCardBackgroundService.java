package com.kaipai.service.actor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kaipai.mapper.actor.ActorCardBackgroundMapper;
import com.kaipai.model.actor.card.dto.ActorCardBackgroundLibraryRespDTO;
import com.kaipai.model.actor.card.entity.ActorCardBackground;
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

    /**
     * 按风格加载背景图库，只返回 enabled=1 的图片，按 sort_order 排序。
     * 背景图不进用户素材库，只从此处读取。
     * 发版防御：表尚未预置（V20260814_002 未执行）时捕获 BadSqlGrammarException，
     * 返回空列表而非 500「操作失败」，避免首页模板区整体报错；预置后自动恢复。
     */
    public ActorCardBackgroundLibraryRespDTO listByStyle(String style) {
        List<ActorCardBackground> items;
        try {
            items = backgroundMapper.selectList(
                    new LambdaQueryWrapper<ActorCardBackground>()
                            .eq(ActorCardBackground::getStyle, style)
                            .eq(ActorCardBackground::getEnabled, 1)
                            .orderByAsc(ActorCardBackground::getSortOrder));
        } catch (org.springframework.dao.InvalidDataAccessResourceUsageException ex) {
            log.warn("[background-library] actor_card_background 表不可用（可能未执行 V20260814_002 预置迁移），返回空图库: {}", ex.getMessage());
            items = List.of();
        }

        ActorCardBackgroundLibraryRespDTO dto = new ActorCardBackgroundLibraryRespDTO();
        dto.setStyle(style);
        dto.setImages(items.stream().map(bg -> {
            ActorCardBackgroundLibraryRespDTO.BackgroundItem item =
                    new ActorCardBackgroundLibraryRespDTO.BackgroundItem();
            item.setId(bg.getId());
            item.setImageUrl(bg.getImageUrl());
            item.setThumbnailUrl(bg.getThumbnailUrl());
            item.setSortOrder(bg.getSortOrder());
            return item;
        }).collect(Collectors.toList()));
        return dto;
    }
}
