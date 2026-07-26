package com.kaipai.model.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_profile_import_request_audit")
public class AiProfileImportRequestAudit extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long auditId;
    private String requestId;
    private Long userId;
    private Long configId;
    private String modelName;
    private String scene;
    private String promptTemplateCode;
    private Long promptVersionId;
    private Integer promptVersionNo;
    private String promptSchemaVersion;
    private String promptContractVersion;
    private String promptRuntimeSha256;
    private String status;
    private Integer inputLength;
    private Integer candidateCount;
    private Integer workCount;
    private Integer conflictCount;
    private Long elapsedMs;
    private String errorCode;
    private Long profileVersion;
    private Long workLibraryVersion;
    private String applyPayloadSha256;
    private String applyStatus;
    private String applyResultSummaryJson;
    private LocalDateTime appliedAt;
}
