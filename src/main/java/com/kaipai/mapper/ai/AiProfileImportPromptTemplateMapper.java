package com.kaipai.mapper.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.ai.entity.AiProfileImportPromptTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiProfileImportPromptTemplateMapper
        extends BaseMapper<AiProfileImportPromptTemplate> {

    @Select("SELECT * FROM ai_profile_import_prompt_template "
            + "WHERE template_code=#{templateCode} AND deleted=0 LIMIT 1 FOR UPDATE")
    AiProfileImportPromptTemplate selectByCodeForUpdate(
            @Param("templateCode") String templateCode);

    @Select("SELECT * FROM ai_profile_import_prompt_template "
            + "WHERE template_id=#{templateId} AND deleted=0 LIMIT 1 FOR UPDATE")
    AiProfileImportPromptTemplate selectByIdForUpdate(@Param("templateId") Long templateId);

    @Select("SELECT * FROM ai_profile_import_prompt_template "
            + "WHERE scene=#{scene} AND deleted=0 LIMIT 1")
    AiProfileImportPromptTemplate selectByScene(@Param("scene") String scene);

    @Update("UPDATE ai_profile_import_prompt_template "
            + "SET draft_version_id=#{draftVersionId}, version=version+1, "
            + "last_update=CURRENT_TIMESTAMP "
            + "WHERE template_id=#{templateId} AND deleted=0 AND version=#{expectedVersion} "
            + "AND draft_version_id IS NULL")
    int attachDraftIfExpected(
            @Param("templateId") Long templateId,
            @Param("draftVersionId") Long draftVersionId,
            @Param("expectedVersion") Integer expectedVersion);

    @Update("UPDATE ai_profile_import_prompt_template "
            + "SET draft_version_id=NULL, version=version+1, last_update=CURRENT_TIMESTAMP "
            + "WHERE template_id=#{templateId} AND deleted=0 AND version=#{expectedVersion} "
            + "AND draft_version_id=#{draftVersionId}")
    int clearDraftIfExpected(
            @Param("templateId") Long templateId,
            @Param("draftVersionId") Long draftVersionId,
            @Param("expectedVersion") Integer expectedVersion);

    @Update("UPDATE ai_profile_import_prompt_template "
            + "SET active_version_id=#{draftVersionId}, draft_version_id=NULL, "
            + "version=version+1, last_update=CURRENT_TIMESTAMP "
            + "WHERE template_id=#{templateId} AND deleted=0 AND version=#{expectedVersion} "
            + "AND draft_version_id=#{draftVersionId}")
    int publishDraftIfExpected(
            @Param("templateId") Long templateId,
            @Param("draftVersionId") Long draftVersionId,
            @Param("expectedVersion") Integer expectedVersion);

    @Update("UPDATE ai_profile_import_prompt_template "
            + "SET active_version_id=#{targetVersionId}, version=version+1, "
            + "last_update=CURRENT_TIMESTAMP "
            + "WHERE template_id=#{templateId} AND deleted=0 AND version=#{expectedVersion} "
            + "AND (active_version_id<>#{targetVersionId} OR active_version_id IS NULL)")
    int restoreActiveIfExpected(
            @Param("templateId") Long templateId,
            @Param("targetVersionId") Long targetVersionId,
            @Param("expectedVersion") Integer expectedVersion);
}
