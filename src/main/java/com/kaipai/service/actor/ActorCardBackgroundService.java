package com.kaipai.service.actor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kaipai.mapper.actor.ActorCardBackgroundMapper;
import com.kaipai.model.actor.card.dto.ActorCardBackgroundLibraryRespDTO;
import com.kaipai.model.actor.card.entity.ActorCardBackground;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActorCardBackgroundService {

    private final ActorCardBackgroundMapper backgroundMapper;

    /**
     * 按风格加载背景图库，只返回 enabled=1 的图片，按 sort_order 排序。
     * 背景图不进用户素材库，只从此处读取。
     */
    public ActorCardBackgroundLibraryRespDTO listByStyle(String style) {
        List<ActorCardBackground> items = backgroundMapper.selectList(
                new LambdaQueryWrapper<ActorCardBackground>()
                        .eq(ActorCardBackground::getStyle, style)
                        .eq(ActorCardBackground::getEnabled, 1)
                        .orderByAsc(ActorCardBackground::getSortOrder));

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
