package com.kaipai.model.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_profile_import_prompt_template")
public class AiProfileImportPromptTemplate extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long templateId;
    private String templateCode;
    private String scene;
    private String displayName;
    private Long activeVersionId;
    private Long draftVersionId;
}
