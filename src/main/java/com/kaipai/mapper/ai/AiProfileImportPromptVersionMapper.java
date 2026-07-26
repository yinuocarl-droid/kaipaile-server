package com.kaipai.mapper.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.ai.entity.AiProfileImportPromptVersion;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiProfileImportPromptVersionMapper
        extends BaseMapper<AiProfileImportPromptVersion> {

    @Select("SELECT * FROM ai_profile_import_prompt_version "
            + "WHERE template_id=#{templateId} AND prompt_version_id=#{promptVersionId} "
            + "AND deleted=0 LIMIT 1 FOR UPDATE")
    AiProfileImportPromptVersion selectOwnedForUpdate(
            @Param("templateId") Long templateId,
            @Param("promptVersionId") Long promptVersionId);

    @Select("SELECT * FROM ai_profile_import_prompt_version "
            + "WHERE template_id=#{templateId} AND prompt_version_id=#{promptVersionId} "
            + "AND deleted=0 LIMIT 1")
    AiProfileImportPromptVersion selectOwned(
            @Param("templateId") Long templateId,
            @Param("promptVersionId") Long promptVersionId);

    @Select("SELECT prompt_version_id, template_id, version_no, version_label, lifecycle_status, "
            + "content_sha256, test_status, tested_content_sha256, tested_runtime_sha256, "
            + "test_fixture_code, test_fixture_version, test_fixture_sha256, tested_model_name, "
            + "tested_config_version, test_candidate_count, test_work_count, test_elapsed_ms, "
            + "test_error_code, tested_by, tested_at, released_by, released_at, version, deleted, "
            + "rid, create_user_id, create_user_name, create_time, update_user_id, "
            + "update_user_name, last_update FROM ai_profile_import_prompt_version "
            + "WHERE template_id=#{templateId} AND deleted=0 "
            + "ORDER BY version_no DESC, prompt_version_id DESC")
    List<AiProfileImportPromptVersion> selectSummariesByTemplateId(
            @Param("templateId") Long templateId);

    @Select("SELECT * FROM ai_profile_import_prompt_version "
            + "WHERE template_id=#{templateId} AND prompt_version_id=#{promptVersionId} "
            + "AND deleted=0 LIMIT 1")
    AiProfileImportPromptVersion selectOwnedDetail(
            @Param("templateId") Long templateId,
            @Param("promptVersionId") Long promptVersionId);

    @Update("UPDATE ai_profile_import_prompt_version SET "
            + "version_label=#{draft.versionLabel}, "
            + "system_prompt_body=#{draft.systemPromptBody}, "
            + "repair_prompt_body=#{draft.repairPromptBody}, "
            + "change_summary=#{draft.changeSummary}, "
            + "test_status=CASE WHEN content_sha256<>#{draft.contentSha256} "
            + "THEN 'stale' ELSE test_status END, "
            + "content_sha256=#{draft.contentSha256}, version=version+1, "
            + "last_update=CURRENT_TIMESTAMP "
            + "WHERE prompt_version_id=#{draft.promptVersionId} "
            + "AND template_id=#{draft.templateId} AND lifecycle_status='draft' AND deleted=0 "
            + "AND version=#{expectedVersion}")
    int updateDraftIfExpected(
            @Param("draft") AiProfileImportPromptVersion draft,
            @Param("expectedVersion") Integer expectedVersion);

    @Update("UPDATE ai_profile_import_prompt_version "
            + "SET lifecycle_status='abandoned', version=version+1, "
            + "last_update=CURRENT_TIMESTAMP "
            + "WHERE prompt_version_id=#{promptVersionId} AND template_id=#{templateId} "
            + "AND lifecycle_status='draft' AND deleted=0 AND version=#{expectedVersion}")
    int abandonDraftIfExpected(
            @Param("templateId") Long templateId,
            @Param("promptVersionId") Long promptVersionId,
            @Param("expectedVersion") Integer expectedVersion);

    @Update("UPDATE ai_profile_import_prompt_version SET "
            + "test_status=#{snapshot.testStatus}, "
            + "tested_content_sha256=#{snapshot.testedContentSha256}, "
            + "tested_runtime_sha256=#{snapshot.testedRuntimeSha256}, "
            + "test_fixture_code=#{snapshot.testFixtureCode}, "
            + "test_fixture_version=#{snapshot.testFixtureVersion}, "
            + "test_fixture_sha256=#{snapshot.testFixtureSha256}, "
            + "tested_model_name=#{snapshot.testedModelName}, "
            + "tested_config_version=#{snapshot.testedConfigVersion}, "
            + "test_candidate_count=#{snapshot.testCandidateCount}, "
            + "test_work_count=#{snapshot.testWorkCount}, "
            + "test_elapsed_ms=#{snapshot.testElapsedMs}, "
            + "test_error_code=#{snapshot.testErrorCode}, tested_by=#{snapshot.testedBy}, "
            + "tested_at=#{snapshot.testedAt}, version=version+1, last_update=CURRENT_TIMESTAMP "
            + "WHERE prompt_version_id=#{snapshot.promptVersionId} "
            + "AND template_id=#{snapshot.templateId} AND deleted=0 "
            + "AND lifecycle_status IN ('draft','released') "
            + "AND version=#{snapshot.version} AND content_sha256=#{snapshot.contentSha256}")
    int writeTestResultIfSnapshotMatches(
            @Param("snapshot") AiProfileImportPromptVersion snapshot);

    @Update("UPDATE ai_profile_import_prompt_version "
            + "SET lifecycle_status='released', released_by=#{snapshot.releasedBy}, "
            + "released_at=#{snapshot.releasedAt}, version=version+1, "
            + "last_update=CURRENT_TIMESTAMP "
            + "WHERE prompt_version_id=#{snapshot.promptVersionId} "
            + "AND template_id=#{snapshot.templateId} "
            + "AND lifecycle_status='draft' AND deleted=0 AND version=#{snapshot.version} "
            + "AND content_sha256=#{snapshot.contentSha256} AND test_status='success' "
            + "AND tested_content_sha256=#{snapshot.contentSha256} "
            + "AND tested_runtime_sha256=#{snapshot.testedRuntimeSha256} "
            + "AND test_fixture_code=#{snapshot.testFixtureCode} "
            + "AND test_fixture_version=#{snapshot.testFixtureVersion} "
            + "AND test_fixture_sha256=#{snapshot.testFixtureSha256} "
            + "AND tested_model_name=#{snapshot.testedModelName} "
            + "AND tested_config_version=#{snapshot.testedConfigVersion}")
    int freezeDraftIfTestSnapshotMatches(
            @Param("snapshot") AiProfileImportPromptVersion snapshot);
}
