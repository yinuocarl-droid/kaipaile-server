package com.kaipai.model.actor.card.entity;

import lombok.Data;

import java.util.Arrays;
import java.util.List;

/**
 * 演员卡生成设置（步骤7）
 * 从 actor_card.settings_json 解析
 * Requirements: 00-215 § 3.3
 */
@Data
public class ActorCardSettings {

    /** 是否展示联系方式 */
    private Boolean showContact;

    /** 是否展示视频简历 */
    private Boolean showVideo;

    /** 是否展示附件简历 */
    private Boolean showAttachment;

    /** 模块展示顺序 */
    private List<String> moduleOrder;

    /**
     * 默认设置
     */
    public static ActorCardSettings defaults() {
        ActorCardSettings settings = new ActorCardSettings();
        settings.setShowContact(true);
        settings.setShowVideo(true);
        settings.setShowAttachment(true);
        settings.setModuleOrder(Arrays.asList("works", "photos", "video", "attachment"));
        return settings;
    }

    /**
     * 是否展示联系方式（空值视为 true）
     */
    public boolean isShowContact() {
        return showContact == null || showContact;
    }

    /**
     * 是否展示视频简历（空值视为 true）
     */
    public boolean isShowVideo() {
        return showVideo == null || showVideo;
    }

    /**
     * 是否展示附件简历（空值视为 true）
     */
    public boolean isShowAttachment() {
        return showAttachment == null || showAttachment;
    }

    /**
     * 获取模块顺序（空值返回默认）
     */
    public List<String> getModuleOrder() {
        if (moduleOrder == null || moduleOrder.isEmpty()) {
            return Arrays.asList("works", "photos", "video", "attachment");
        }
        return moduleOrder;
    }
}
