package com.kaipai.model.actor.card.dto;

import lombok.Data;

/** 00-267: 首页「模板创建」卡片 = 一个启用的风格模板 + 其「页面装修」编辑背景（未配置为 null）。 */
@Data
public class ActorCardTemplateCardRespDTO {
    private Long templateId;
    private String templateSceneCode;
    private String name;
    private ActorCardBackgroundLibraryRespDTO.BackgroundItem background;
}
