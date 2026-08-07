package com.kaipai.service.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaipai.common.exception.BizException;
import com.kaipai.integration.ai.profileimport.DeepSeekProfileTextExtractor;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.ai.dto.ProfileImportPromptTestResultRespDTO;
import com.kaipai.model.ai.entity.AiProfileImportConfig;
import com.kaipai.model.ai.entity.AiProfileImportPromptTemplate;
import com.kaipai.model.ai.entity.AiProfileImportPromptVersion;
import com.kaipai.service.ai.ProfileImportPromptTester;
import com.kaipai.service.ai.ProfileImportRuntimeConfig;
import com.kaipai.service.ai.profileimport.ProfileImportPromptFixtureCatalog;
import com.kaipai.service.ai.profileimport.ProfileImportPromptFixtureCatalog.Fixture;
import com.kaipai.service.ai.profileimport.ProfileImportPromptRenderer;
import com.kaipai.service.ai.profileimport.ProfileImportPromptRuntime;
import com.kaipai.service.ai.profileimport.ProfileImportSchemaValidator;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public final class ProfileImportPromptTesterImpl implements ProfileImportPromptTester {

    private static final Set<ProfileDomainErrorCode> TEST_FAILURE_ERRORS = Set.of(
            ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE,
            ProfileDomainErrorCode.PROFILE_IMPORT_MODEL_TIMEOUT,
            ProfileDomainErrorCode.PROFILE_IMPORT_RESPONSE_INVALID);

    private final DeepSeekProfileTextExtractor extractor;
    private final ProfileImportPromptRenderer renderer;
    private final ProfileImportSchemaValidator validator;
    private final ProfileImportPromptFixtureCatalog fixtureCatalog;

    public ProfileImportPromptTesterImpl(
            DeepSeekProfileTextExtractor extractor,
            ProfileImportPromptRenderer renderer,
            ProfileImportSchemaValidator validator,
            ProfileImportPromptFixtureCatalog fixtureCatalog) {
        this.extractor = extractor;
        this.renderer = renderer;
        this.validator = validator;
        this.fixtureCatalog = fixtureCatalog;
    }

    @Override
    public ProfileImportPromptTestResultRespDTO execute(
            AiProfileImportPromptTemplate template,
            AiProfileImportPromptVersion version,
            ProfileImportRuntimeConfig runtimeConfig) {
        long startedAt = System.nanoTime();
        ProfileImportPromptTestResultRespDTO result = new ProfileImportPromptTestResultRespDTO();
        result.setPromptVersionId(version == null ? null : version.getPromptVersionId());
        result.setModelName(runtimeConfig == null ? null : runtimeConfig.modelName());
        result.setConfigVersion(runtimeConfig == null ? null : runtimeConfig.configVersion());
        result.setCandidateCount(0);
        result.setWorkCount(0);
        try {
            Objects.requireNonNull(template);
            Objects.requireNonNull(version);
            Objects.requireNonNull(runtimeConfig);
            ProfileImportPromptRuntime promptRuntime = renderer.render(template, version);
            String contentSha256 = renderer.contentSha256(template, version);
            Fixture fixture = fixtureCatalog.load(template.getScene());
            result.setContentSha256(contentSha256);
            result.setRuntimeSha256(promptRuntime.runtimeSha256());
            result.setFixtureCode(fixture.code());
            result.setFixtureVersion(fixture.version());
            result.setFixtureSha256(fixture.sha256());

            AiProfileImportConfig config = providerConfig(runtimeConfig);
            JsonNode response = extractor.extract(
                    config,
                    runtimeConfig.apiKey(),
                    promptRuntime,
                    fixture.body(),
                    "prompt-fixture-test");
            ProfileImportSchemaValidator.ValidatedExtraction extraction;
            try {
                extraction = validator.validate(
                        response.toString(), fixture.body(), template.getScene());
            } catch (IllegalArgumentException error) {
                return failed(
                        result,
                        ProfileDomainErrorCode.PROFILE_IMPORT_RESPONSE_INVALID.errorCode(),
                        startedAt);
            }
            if (!hasExpectedCandidates(template.getScene(), extraction)) {
                return failed(
                        result,
                        ProfileDomainErrorCode.PROFILE_IMPORT_RESPONSE_INVALID.errorCode(),
                        startedAt);
            }
            result.setStatus("success");
            result.setCandidateCount(extraction.profileCandidates().size());
            result.setWorkCount(extraction.workCandidates().size());
            result.setErrorCode(null);
            result.setElapsedMs(elapsedMillis(startedAt));
            return result;
        } catch (BizException error) {
            return failed(result, stableErrorCode(error), startedAt);
        } catch (RuntimeException error) {
            return failed(
                    result,
                    ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.errorCode(),
                    startedAt);
        }
    }

    private boolean hasExpectedCandidates(
            String scene,
            ProfileImportSchemaValidator.ValidatedExtraction extraction) {
        if ("full_profile".equals(scene)) {
            return !extraction.profileCandidates().isEmpty()
                    && !extraction.workCandidates().isEmpty();
        }
        return "works_only".equals(scene) && !extraction.workCandidates().isEmpty();
    }

    private AiProfileImportConfig providerConfig(ProfileImportRuntimeConfig runtime) {
        AiProfileImportConfig config = new AiProfileImportConfig();
        config.setConfigId(runtime.configId());
        config.setVersion(runtime.configVersion());
        config.setEndpoint(runtime.endpoint());
        config.setModelName(runtime.modelName());
        config.setConnectTimeoutMs(runtime.connectTimeoutMs());
        config.setReadTimeoutMs(runtime.readTimeoutMs());
        config.setMaxInputChars(runtime.maxInputChars());
        config.setMaxOutputTokens(runtime.maxOutputTokens());
        config.setPerUserDailyLimit(runtime.dailyLimit());
        return config;
    }

    private ProfileImportPromptTestResultRespDTO failed(
            ProfileImportPromptTestResultRespDTO result,
            String errorCode,
            long startedAt) {
        result.setStatus("failed");
        result.setCandidateCount(0);
        result.setWorkCount(0);
        result.setErrorCode(errorCode);
        result.setElapsedMs(elapsedMillis(startedAt));
        return result;
    }

    private String stableErrorCode(BizException error) {
        return TEST_FAILURE_ERRORS.stream()
                .filter(candidate -> candidate.code() == error.getCode())
                .map(ProfileDomainErrorCode::errorCode)
                .findFirst()
                .orElse(ProfileDomainErrorCode.PROFILE_IMPORT_UNAVAILABLE.errorCode());
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

}
