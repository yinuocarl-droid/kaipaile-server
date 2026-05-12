package com.kaipai.module.model.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_image_provider_config")
public class AiImageProviderConfig extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long configId;

    private String providerCode;

    private String displayName;

    private Integer enabled;

    private Integer active;

    private Integer priority;

    private String publicConfigJson;

    private String secretConfigCiphertext;

    private String secretMaskJson;

    private Long secretUpdatedBy;

    private String secretUpdatedByName;

    private LocalDateTime secretUpdatedAt;

    private String lastTestStatus;

    private String lastTestMessage;

    private LocalDateTime lastTestAt;
}
