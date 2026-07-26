package com.kaipai.mapper.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kaipai.model.ai.entity.AiProfileImportConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiProfileImportConfigMapper extends BaseMapper<AiProfileImportConfig> {
    @Select("SELECT * FROM ai_profile_import_config "
            + "WHERE provider_code=#{providerCode} AND deleted=0 LIMIT 1 FOR UPDATE")
    AiProfileImportConfig selectByProviderCodeForUpdate(
            @Param("providerCode") String providerCode);
}
