package com.kaipai.module.model.membership.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * Membership product definitions.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("membership_product")
public class MembershipProduct extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long productId;
    private String productCode;
    private String productName;
    private Integer membershipTier;
    private Integer durationDays;
    private BigDecimal listPrice;
    private BigDecimal salePrice;
    private Integer status;
    private String benefitConfigJson;
    private Integer sortNo;
}
