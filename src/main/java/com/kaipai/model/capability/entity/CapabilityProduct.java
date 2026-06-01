package com.kaipai.model.capability.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * Capability product definitions.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("capability_product")
public class CapabilityProduct extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long productId;
    private String productCode;
    private String productName;
    private Integer capabilityTier;
    private Integer durationDays;
    private BigDecimal listPrice;
    private BigDecimal salePrice;
    private Integer status;
    private String benefitConfigJson;
    private Integer sortNo;
}
