package com.kaipai.mapper.ai;

import com.kaipai.model.ai.entity.AiProfileImportPromptAudit;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiProfileImportPromptAuditMapper {

    @Insert("INSERT INTO ai_profile_import_prompt_audit "
            + "(template_id, prompt_version_id, action_code, from_version_id, to_version_id, "
            + "content_sha256, runtime_sha256, schema_version, contract_version, fixture_code, "
            + "fixture_version, fixture_sha256, model_name, config_version, test_operator_id, "
            + "tested_at, operator_id, operator_name, reason_code, result_status, error_code, "
            + "message) VALUES "
            + "(#{templateId}, #{promptVersionId}, #{actionCode}, #{fromVersionId}, "
            + "#{toVersionId}, #{contentSha256}, #{runtimeSha256}, #{schemaVersion}, "
            + "#{contractVersion}, #{fixtureCode}, #{fixtureVersion}, #{fixtureSha256}, "
            + "#{modelName}, #{configVersion}, #{testOperatorId}, #{testedAt}, #{operatorId}, "
            + "#{operatorName}, #{reasonCode}, #{resultStatus}, #{errorCode}, #{message})")
    @Options(useGeneratedKeys = true, keyProperty = "promptAuditId")
    int insertAudit(AiProfileImportPromptAudit audit);

    @Select("SELECT * FROM ai_profile_import_prompt_audit WHERE deleted=0 "
            + "ORDER BY create_time DESC, prompt_audit_id DESC LIMIT #{limit}")
    List<AiProfileImportPromptAudit> selectRecent(@Param("limit") Integer limit);
}
