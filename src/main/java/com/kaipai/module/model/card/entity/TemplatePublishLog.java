package com.kaipai.module.model.card.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("template_publish_log")
public class TemplatePublishLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long publishLogId;
    private Long templateId;
    private String targetType;
    private String targetCode;
    private String publishVersion;
    private String draftVersion;
    private String sourceVersion;
    private String targetVersion;
    private String actionType;
    private Long publishedBy;
    private String publishNote;
    private String diffSummaryJson;
    private String snapshotJson;
    private LocalDateTime publishedAt;
}
