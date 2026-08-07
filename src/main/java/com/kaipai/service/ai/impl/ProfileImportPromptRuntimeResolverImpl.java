package com.kaipai.service.ai.impl;

import com.kaipai.mapper.ai.AiProfileImportPromptTemplateMapper;
import com.kaipai.mapper.ai.AiProfileImportPromptVersionMapper;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.ai.entity.AiProfileImportPromptTemplate;
import com.kaipai.model.ai.entity.AiProfileImportPromptVersion;
import com.kaipai.service.ai.ProfileImportPromptRuntimeResolver;
import com.kaipai.service.ai.profileimport.ProfileImportPromptRenderer;
import com.kaipai.service.ai.profileimport.ProfileImportPromptRuntime;
import com.kaipai.service.ai.profileimport.ProfileImportSceneGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileImportPromptRuntimeResolverImpl
        implements ProfileImportPromptRuntimeResolver {

    private final AiProfileImportPromptTemplateMapper templateMapper;
    private final AiProfileImportPromptVersionMapper versionMapper;
    private final ProfileImportPromptRenderer renderer;

    @Override
    public ProfileImportPromptRuntime resolve(String scene) {
        try {
            String supportedScene = ProfileImportSceneGuard.requireSupported(scene);
            AiProfileImportPromptTemplate template =
                    templateMapper.selectByScene(supportedScene);
            require(template != null && Integer.valueOf(0).equals(template.getDeleted()));
            require(supportedScene.equals(template.getScene()));
            require(template.getActiveVersionId() != null);
            AiProfileImportPromptVersion version = versionMapper.selectOwned(
                    template.getTemplateId(), template.getActiveVersionId());
            require(version != null && Integer.valueOf(0).equals(version.getDeleted()));
            require(template.getTemplateId().equals(version.getTemplateId()));
            require("released".equals(version.getLifecycleStatus()));
            require(renderer.contentSha256(template, version)
                    .equals(version.getContentSha256()));
            ProfileImportPromptRuntime runtime = renderer.render(template, version);
            require(runtime.promptVersionId().equals(template.getActiveVersionId()));
            return runtime;
        } catch (RuntimeException error) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.toException();
        }
    }

    private void require(boolean condition) {
        if (!condition) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.toException();
        }
    }
}
