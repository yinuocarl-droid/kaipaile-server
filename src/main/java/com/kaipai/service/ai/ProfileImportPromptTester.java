package com.kaipai.service.ai;

import com.kaipai.model.ai.dto.ProfileImportPromptTestResultRespDTO;
import com.kaipai.model.ai.entity.AiProfileImportPromptTemplate;
import com.kaipai.model.ai.entity.AiProfileImportPromptVersion;

public interface ProfileImportPromptTester {

    ProfileImportPromptTestResultRespDTO execute(
            AiProfileImportPromptTemplate template,
            AiProfileImportPromptVersion version,
            ProfileImportRuntimeConfig runtimeConfig);
}
