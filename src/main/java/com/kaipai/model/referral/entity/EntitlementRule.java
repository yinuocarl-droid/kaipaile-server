package com.kaipai.model.referral.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("entitlement_rule")
public class EntitlementRule extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long ruleId;

    private String ruleCode;

    private String ruleName;

    private String grantType;

    private String triggerType;

    private String ruleConfigJson;

    private Integer enabled;
}
