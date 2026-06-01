package com.kaipai.model.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_image_provider_config_audit")
public class AiImageProviderConfigAudit extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long auditId;

    private Long configId;

    private String providerCode;

    private String actionCode;

    private String beforePublicConfigJson;

    private String afterPublicConfigJson;

    private String beforeSecretMaskJson;

    private String afterSecretMaskJson;

    private Long operatorId;

    private String operatorName;

    private String resultStatus;

    private String message;
}
