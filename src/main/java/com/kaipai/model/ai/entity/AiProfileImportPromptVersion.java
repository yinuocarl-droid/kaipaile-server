package com.kaipai.model.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kaipai.common.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_profile_import_prompt_version")
public class AiProfileImportPromptVersion extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long promptVersionId;
    private Long templateId;
    private Integer versionNo;
    private String versionLabel;
    private String lifecycleStatus;
    private String systemPromptBody;
    private String repairPromptBody;
    private String schemaVersion;
    private String contractVersion;
    private String contentSha256;
    private String changeSummary;
    private String testStatus;
    private String testedContentSha256;
    private String testedRuntimeSha256;
    private String testFixtureCode;
    private String testFixtureVersion;
    private String testFixtureSha256;
    private String testedModelName;
    private Integer testedConfigVersion;
    private Integer testCandidateCount;
    private Integer testWorkCount;
    private Long testElapsedMs;
    private String testErrorCode;
    private Long testedBy;
    private LocalDateTime testedAt;
    private Long releasedBy;
    private LocalDateTime releasedAt;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long openDraftTemplateId;
}
