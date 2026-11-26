package com.kaipai.model.verify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("identity_verification")
public class IdentityVerification extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long verificationId;

    private Long userId;

    private String realName;

    private String idCardNoCipher;

    private String idCardNoMasked;

    private String idCardHash;

    /** 1 pending, 2 approved, 3 rejected */
    private Integer status;

    private String rejectReason;

    private Long reviewerId;

    private LocalDateTime reviewedAt;

    /** profile completion snapshot captured on submit */
    private Integer snapshotProfileCompletion;

    private String providerResultCode;

    private String providerRequestId;

    private String providerCode;

    private String providerResultMessage;

    private LocalDateTime providerVerifiedAt;
}
