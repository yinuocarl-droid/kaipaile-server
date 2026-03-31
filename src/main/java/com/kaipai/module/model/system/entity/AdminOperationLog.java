package com.kaipai.module.model.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_operation_log")
public class AdminOperationLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long operationLogId;
    private Long adminUserId;
    private String adminUserName;
    private String moduleCode;
    private String operationCode;
    private String targetType;
    private Long targetId;
    private String requestId;
    private String clientIp;
    private String userAgent;
    private String beforeSnapshotJson;
    private String afterSnapshotJson;
    private Integer operationResult;
    private String failReason;
    private String extraContextJson;
    private String confirmToken;
    private LocalDateTime confirmedAt;
}
