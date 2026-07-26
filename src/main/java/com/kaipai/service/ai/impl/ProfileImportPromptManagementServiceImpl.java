package com.kaipai.service.ai.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminAuthenticatedUser;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.ResultCode;
import com.kaipai.mapper.ai.AiProfileImportPromptAuditMapper;
import com.kaipai.mapper.ai.AiProfileImportPromptTemplateMapper;
import com.kaipai.mapper.ai.AiProfileImportPromptVersionMapper;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.ai.dto.ProfileImportPromptAuditRespDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptCreateDraftReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptRestoreReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptStrictWriteDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptTemplateSummaryRespDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptTestResultRespDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptUpdateDraftReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptVersionActionReqDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptVersionDetailRespDTO;
import com.kaipai.model.ai.dto.ProfileImportPromptVersionSummaryRespDTO;
import com.kaipai.model.ai.entity.AiProfileImportPromptAudit;
import com.kaipai.model.ai.entity.AiProfileImportPromptTemplate;
import com.kaipai.model.ai.entity.AiProfileImportPromptVersion;
import com.kaipai.service.ai.ProfileImportPromptManagementService;
import com.kaipai.service.ai.profileimport.ProfileImportPromptReasonCode;
import com.kaipai.service.ai.profileimport.ProfileImportPromptRenderer;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProfileImportPromptManagementServiceImpl
        implements ProfileImportPromptManagementService {

    private static final String LIFECYCLE_DRAFT = "draft";
    private static final String LIFECYCLE_RELEASED = "released";
    private static final String TEST_UNTESTED = "untested";
    private static final String RESULT_SUCCESS = "success";

    private final AiProfileImportPromptTemplateMapper templateMapper;
    private final AiProfileImportPromptVersionMapper versionMapper;
    private final AiProfileImportPromptAuditMapper auditMapper;
    private final ProfileImportPromptRenderer renderer;
    private final AdminAuthContext adminAuthContext;

    @Override
    public List<ProfileImportPromptTemplateSummaryRespDTO> templates() {
        List<AiProfileImportPromptTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<AiProfileImportPromptTemplate>()
                        .eq(AiProfileImportPromptTemplate::getDeleted, 0)
                        .orderByAsc(AiProfileImportPromptTemplate::getTemplateId));
        if (templates == null || templates.isEmpty()) {
            return Collections.emptyList();
        }
        return templates.stream()
                .filter(ProfileImportPromptManagementServiceImpl::isUndeleted)
                .map(this::toTemplateSummary)
                .toList();
    }

    @Override
    public List<ProfileImportPromptVersionSummaryRespDTO> versions(String templateCode) {
        if (templateCode == null || templateCode.isBlank()) {
            throw invalid();
        }
        AiProfileImportPromptTemplate template = templateMapper.selectOne(
                new LambdaQueryWrapper<AiProfileImportPromptTemplate>()
                        .eq(AiProfileImportPromptTemplate::getTemplateCode, templateCode)
                        .eq(AiProfileImportPromptTemplate::getDeleted, 0));
        requireReadableTemplate(template);
        return summaries(template.getTemplateId()).stream()
                .map(ProfileImportPromptManagementServiceImpl::toVersionSummary)
                .toList();
    }

    @Override
    public ProfileImportPromptVersionDetailRespDTO version(Long promptVersionId) {
        AiProfileImportPromptVersion locator = locateVersion(promptVersionId);
        AiProfileImportPromptVersion detail =
                versionMapper.selectOwnedDetail(locator.getTemplateId(), promptVersionId);
        requireOwnedVersion(detail, locator.getTemplateId(), promptVersionId);
        return toVersionDetail(detail);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProfileImportPromptTemplateSummaryRespDTO createDraft(
            Long operatorId,
            String templateCode,
            ProfileImportPromptCreateDraftReqDTO request) {
        requireStrictRequest(request);
        if (templateCode == null
                || templateCode.isBlank()
                || request.getExpectedTemplateVersion() == null) {
            throw invalid();
        }
        AdminAuthenticatedUser admin = requireOperator(operatorId);
        AiProfileImportPromptTemplate template =
                templateMapper.selectByCodeForUpdate(templateCode);
        if (!isUndeleted(template)
                || !Objects.equals(templateCode, template.getTemplateCode())
                || template.getDraftVersionId() != null) {
            throw stateConflict();
        }
        if (!Objects.equals(request.getExpectedTemplateVersion(), template.getVersion())) {
            throw versionConflict();
        }

        boolean currentSource = request.getSourceVersionId() == null;
        Long sourceVersionId = currentSource
                ? template.getActiveVersionId()
                : request.getSourceVersionId();
        if (sourceVersionId == null) {
            throw stateConflict();
        }
        AiProfileImportPromptVersion source =
                versionMapper.selectOwnedForUpdate(template.getTemplateId(), sourceVersionId);
        if (!isOwnedVersion(source, template.getTemplateId(), sourceVersionId)
                || !LIFECYCLE_RELEASED.equals(source.getLifecycleStatus())) {
            throw stateConflict();
        }

        Integer nextVersionNo = versionMapper.selectNextVersionNo(template.getTemplateId());
        if (nextVersionNo == null || nextVersionNo < 1) {
            throw stateConflict();
        }
        AiProfileImportPromptVersion draft = new AiProfileImportPromptVersion();
        draft.setTemplateId(template.getTemplateId());
        draft.setVersionNo(nextVersionNo);
        draft.setVersionLabel(source.getVersionLabel());
        draft.setLifecycleStatus(LIFECYCLE_DRAFT);
        draft.setSystemPromptBody(source.getSystemPromptBody());
        draft.setRepairPromptBody(source.getRepairPromptBody());
        draft.setSchemaVersion(source.getSchemaVersion());
        draft.setContractVersion(source.getContractVersion());
        draft.setTestStatus(TEST_UNTESTED);
        draft.setDeleted(0);
        draft.setCreateUserId(admin.getAdminUserId());
        draft.setCreateUserName(admin.getUserName());
        draft.setUpdateUserId(admin.getAdminUserId());
        draft.setUpdateUserName(admin.getUserName());
        draft.setContentSha256(renderer.contentSha256(template, draft));

        if (versionMapper.insert(draft) != 1 || draft.getPromptVersionId() == null) {
            throw affectedRowsFailure();
        }
        if (templateMapper.attachDraftIfExpected(
                        template.getTemplateId(),
                        draft.getPromptVersionId(),
                        request.getExpectedTemplateVersion())
                != 1) {
            throw versionConflict();
        }

        ProfileImportPromptReasonCode reason = currentSource
                ? ProfileImportPromptReasonCode.DRAFT_CREATED_CURRENT
                : ProfileImportPromptReasonCode.DRAFT_CREATED_HISTORY;
        requireAudit(draftAudit(
                template.getTemplateId(),
                draft,
                "draft_create",
                sourceVersionId,
                draft.getPromptVersionId(),
                reason,
                admin));
        return freshTemplateSummary(template.getTemplateId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProfileImportPromptVersionDetailRespDTO updateDraft(
            Long operatorId,
            Long promptVersionId,
            ProfileImportPromptUpdateDraftReqDTO request) {
        requireStrictRequest(request);
        requireUpdateMetadata(request);
        if (promptVersionId == null || request.getExpectedVersion() == null) {
            throw invalid();
        }
        AdminAuthenticatedUser admin = requireOperator(operatorId);
        AiProfileImportPromptVersion locator = locateVersion(promptVersionId);
        AiProfileImportPromptTemplate template =
                templateMapper.selectByIdForUpdate(locator.getTemplateId());
        AiProfileImportPromptVersion locked =
                versionMapper.selectOwnedForUpdate(locator.getTemplateId(), promptVersionId);
        if (!isUndeleted(template)
                || !isOwnedVersion(locked, locator.getTemplateId(), promptVersionId)
                || !Objects.equals(template.getDraftVersionId(), promptVersionId)
                || !LIFECYCLE_DRAFT.equals(locked.getLifecycleStatus())) {
            throw stateConflict();
        }
        if (!Objects.equals(request.getExpectedVersion(), locked.getVersion())) {
            throw versionConflict();
        }

        AiProfileImportPromptVersion update = new AiProfileImportPromptVersion();
        update.setPromptVersionId(promptVersionId);
        update.setTemplateId(locator.getTemplateId());
        update.setVersionLabel(request.getVersionLabel());
        update.setSystemPromptBody(request.getSystemPromptBody());
        update.setRepairPromptBody(request.getRepairPromptBody());
        update.setChangeSummary(request.getChangeSummary());
        update.setSchemaVersion(locked.getSchemaVersion());
        update.setContractVersion(locked.getContractVersion());
        update.setUpdateUserId(admin.getAdminUserId());
        update.setUpdateUserName(admin.getUserName());
        update.setContentSha256(renderer.contentSha256(template, update));
        if (versionMapper.updateDraftIfExpected(update, request.getExpectedVersion()) != 1) {
            throw versionConflict();
        }

        requireAudit(draftAudit(
                template.getTemplateId(),
                update,
                "draft_update",
                promptVersionId,
                promptVersionId,
                ProfileImportPromptReasonCode.DRAFT_UPDATED,
                admin));
        AiProfileImportPromptVersion fresh =
                versionMapper.selectOwnedDetail(template.getTemplateId(), promptVersionId);
        requireOwnedVersion(fresh, template.getTemplateId(), promptVersionId);
        return toVersionDetail(fresh);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProfileImportPromptTemplateSummaryRespDTO abandonDraft(
            Long operatorId,
            Long promptVersionId,
            ProfileImportPromptVersionActionReqDTO request) {
        requireStrictRequest(request);
        ProfileImportPromptReasonCode reason = ProfileImportPromptReasonCode.requireAbandon(
                request.getReasonCode());
        if (promptVersionId == null
                || request.getExpectedTemplateVersion() == null
                || request.getExpectedVersion() == null) {
            throw invalid();
        }
        AdminAuthenticatedUser admin = requireOperator(operatorId);
        AiProfileImportPromptVersion locator = locateVersion(promptVersionId);
        AiProfileImportPromptTemplate template =
                templateMapper.selectByIdForUpdate(locator.getTemplateId());
        if (!isUndeleted(template) || template.getActiveVersionId() == null) {
            throw stateConflict();
        }
        AiProfileImportPromptVersion active = versionMapper.selectOwned(
                template.getTemplateId(), template.getActiveVersionId());
        if (!isOwnedVersion(active, template.getTemplateId(), template.getActiveVersionId())
                || !LIFECYCLE_RELEASED.equals(active.getLifecycleStatus())) {
            throw stateConflict();
        }
        AiProfileImportPromptVersion target =
                versionMapper.selectOwnedForUpdate(template.getTemplateId(), promptVersionId);
        if (!isOwnedVersion(target, template.getTemplateId(), promptVersionId)
                || !LIFECYCLE_DRAFT.equals(target.getLifecycleStatus())
                || !Objects.equals(template.getDraftVersionId(), promptVersionId)) {
            throw stateConflict();
        }
        if (!Objects.equals(request.getExpectedTemplateVersion(), template.getVersion())
                || !Objects.equals(request.getExpectedVersion(), target.getVersion())) {
            throw versionConflict();
        }

        if (versionMapper.abandonDraftIfExpected(
                        template.getTemplateId(),
                        promptVersionId,
                        request.getExpectedVersion(),
                        admin.getAdminUserId(),
                        admin.getUserName())
                != 1) {
            throw versionConflict();
        }
        if (templateMapper.clearDraftIfExpected(
                        template.getTemplateId(),
                        promptVersionId,
                        request.getExpectedTemplateVersion())
                != 1) {
            throw versionConflict();
        }
        requireAudit(draftAudit(
                template.getTemplateId(),
                target,
                "draft_abandon",
                promptVersionId,
                template.getActiveVersionId(),
                reason,
                admin));
        return freshTemplateSummary(template.getTemplateId());
    }

    @Override
    public ProfileImportPromptTestResultRespDTO test(Long operatorId, Long promptVersionId) {
        requireOperator(operatorId);
        throw stateConflict();
    }

    @Override
    public ProfileImportPromptTemplateSummaryRespDTO publish(
            Long operatorId,
            Long promptVersionId,
            ProfileImportPromptVersionActionReqDTO request) {
        requireStrictRequest(request);
        ProfileImportPromptReasonCode.requirePublish(request.getReasonCode());
        requireOperator(operatorId);
        throw stateConflict();
    }

    @Override
    public ProfileImportPromptTemplateSummaryRespDTO restore(
            Long operatorId,
            String templateCode,
            Long targetVersionId,
            ProfileImportPromptRestoreReqDTO request) {
        requireStrictRequest(request);
        ProfileImportPromptReasonCode.requireRestore(request.getReasonCode());
        requireOperator(operatorId);
        throw stateConflict();
    }

    @Override
    public List<ProfileImportPromptAuditRespDTO> audits() {
        List<AiProfileImportPromptAudit> rows = auditMapper.selectRecent(50);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream()
                .map(ProfileImportPromptManagementServiceImpl::toAuditResponse)
                .toList();
    }

    private ProfileImportPromptTemplateSummaryRespDTO freshTemplateSummary(Long templateId) {
        AiProfileImportPromptTemplate fresh = templateMapper.selectById(templateId);
        requireReadableTemplate(fresh);
        if (!Objects.equals(templateId, fresh.getTemplateId())) {
            throw stateConflict();
        }
        return toTemplateSummary(fresh);
    }

    private ProfileImportPromptTemplateSummaryRespDTO toTemplateSummary(
            AiProfileImportPromptTemplate template) {
        List<AiProfileImportPromptVersion> versions = summaries(template.getTemplateId());
        AiProfileImportPromptVersion active = findVersion(versions, template.getActiveVersionId());
        AiProfileImportPromptVersion draft = findVersion(versions, template.getDraftVersionId());
        if ((template.getActiveVersionId() != null && active == null)
                || (template.getDraftVersionId() != null && draft == null)) {
            throw stateConflict();
        }
        ProfileImportPromptTemplateSummaryRespDTO response =
                new ProfileImportPromptTemplateSummaryRespDTO();
        response.setTemplateId(template.getTemplateId());
        response.setTemplateCode(template.getTemplateCode());
        response.setScene(template.getScene());
        response.setDisplayName(template.getDisplayName());
        response.setActiveVersionId(template.getActiveVersionId());
        if (active != null) {
            response.setActiveVersionNo(active.getVersionNo());
            response.setActiveVersionLabel(active.getVersionLabel());
            response.setActiveContentSha256(active.getContentSha256());
            response.setActiveTestStatus(active.getTestStatus());
        }
        response.setDraftVersionId(template.getDraftVersionId());
        if (draft != null) {
            response.setDraftVersionNo(draft.getVersionNo());
            response.setDraftVersionLabel(draft.getVersionLabel());
            response.setDraftContentSha256(draft.getContentSha256());
            response.setDraftTestStatus(draft.getTestStatus());
        }
        response.setVersion(template.getVersion());
        return response;
    }

    private List<AiProfileImportPromptVersion> summaries(Long templateId) {
        List<AiProfileImportPromptVersion> rows =
                versionMapper.selectSummariesByTemplateId(templateId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream()
                .filter(ProfileImportPromptManagementServiceImpl::isUndeleted)
                .filter(row -> Objects.equals(templateId, row.getTemplateId()))
                .toList();
    }

    private static AiProfileImportPromptVersion findVersion(
            List<AiProfileImportPromptVersion> versions, Long promptVersionId) {
        if (promptVersionId == null) {
            return null;
        }
        return versions.stream()
                .filter(version -> Objects.equals(promptVersionId, version.getPromptVersionId()))
                .findFirst()
                .orElse(null);
    }

    private AiProfileImportPromptVersion locateVersion(Long promptVersionId) {
        if (promptVersionId == null) {
            throw invalid();
        }
        AiProfileImportPromptVersion locator = versionMapper.selectById(promptVersionId);
        if (locator == null || locator.getTemplateId() == null) {
            throw stateConflict();
        }
        return locator;
    }

    private AdminAuthenticatedUser requireOperator(Long operatorId) {
        AdminAuthenticatedUser admin = adminAuthContext.requireCurrentAdmin();
        if (admin == null || !Objects.equals(operatorId, admin.getAdminUserId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return admin;
    }

    private static void requireStrictRequest(ProfileImportPromptStrictWriteDTO request) {
        if (request == null) {
            throw invalid();
        }
        request.requireNoUnexpectedFields();
    }

    private static void requireUpdateMetadata(ProfileImportPromptUpdateDraftReqDTO request) {
        String versionLabel = request.getVersionLabel();
        String changeSummary = request.getChangeSummary();
        if (!StringUtils.hasText(versionLabel)
                || versionLabel.codePointCount(0, versionLabel.length()) > 128
                || (changeSummary != null
                        && changeSummary.codePointCount(0, changeSummary.length()) > 500)) {
            throw invalid();
        }
    }

    private void requireAudit(AiProfileImportPromptAudit audit) {
        if (auditMapper.insertAudit(audit) != 1) {
            throw affectedRowsFailure();
        }
    }

    private static AiProfileImportPromptAudit draftAudit(
            Long templateId,
            AiProfileImportPromptVersion version,
            String actionCode,
            Long fromVersionId,
            Long toVersionId,
            ProfileImportPromptReasonCode reason,
            AdminAuthenticatedUser admin) {
        AiProfileImportPromptAudit audit = new AiProfileImportPromptAudit();
        audit.setTemplateId(templateId);
        audit.setPromptVersionId(version.getPromptVersionId());
        audit.setActionCode(actionCode);
        audit.setFromVersionId(fromVersionId);
        audit.setToVersionId(toVersionId);
        audit.setContentSha256(version.getContentSha256());
        audit.setSchemaVersion(version.getSchemaVersion());
        audit.setContractVersion(version.getContractVersion());
        audit.setOperatorId(admin.getAdminUserId());
        audit.setOperatorName(admin.getUserName());
        audit.setReasonCode(reason.name());
        audit.setResultStatus(RESULT_SUCCESS);
        audit.setErrorCode(null);
        audit.setMessage(null);
        return audit;
    }

    private static void requireReadableTemplate(AiProfileImportPromptTemplate template) {
        if (!isUndeleted(template) || template.getTemplateId() == null) {
            throw stateConflict();
        }
    }

    private static void requireOwnedVersion(
            AiProfileImportPromptVersion version, Long templateId, Long promptVersionId) {
        if (!isOwnedVersion(version, templateId, promptVersionId)) {
            throw stateConflict();
        }
    }

    private static boolean isOwnedVersion(
            AiProfileImportPromptVersion version, Long templateId, Long promptVersionId) {
        return isUndeleted(version)
                && Objects.equals(templateId, version.getTemplateId())
                && Objects.equals(promptVersionId, version.getPromptVersionId());
    }

    private static boolean isUndeleted(AiProfileImportPromptTemplate template) {
        return template != null && Integer.valueOf(0).equals(template.getDeleted());
    }

    private static boolean isUndeleted(AiProfileImportPromptVersion version) {
        return version != null && Integer.valueOf(0).equals(version.getDeleted());
    }

    private static ProfileImportPromptVersionSummaryRespDTO toVersionSummary(
            AiProfileImportPromptVersion version) {
        ProfileImportPromptVersionSummaryRespDTO response =
                new ProfileImportPromptVersionSummaryRespDTO();
        mapVersionSummary(version, response);
        return response;
    }

    private static ProfileImportPromptVersionDetailRespDTO toVersionDetail(
            AiProfileImportPromptVersion version) {
        ProfileImportPromptVersionDetailRespDTO response =
                new ProfileImportPromptVersionDetailRespDTO();
        mapVersionSummary(version, response);
        response.setSystemPromptBody(version.getSystemPromptBody());
        response.setRepairPromptBody(version.getRepairPromptBody());
        response.setSchemaVersion(version.getSchemaVersion());
        response.setContractVersion(version.getContractVersion());
        response.setChangeSummary(version.getChangeSummary());
        return response;
    }

    private static void mapVersionSummary(
            AiProfileImportPromptVersion version,
            ProfileImportPromptVersionSummaryRespDTO response) {
        response.setPromptVersionId(version.getPromptVersionId());
        response.setTemplateId(version.getTemplateId());
        response.setVersionNo(version.getVersionNo());
        response.setVersionLabel(version.getVersionLabel());
        response.setLifecycleStatus(version.getLifecycleStatus());
        response.setContentSha256(version.getContentSha256());
        response.setTestStatus(version.getTestStatus());
        response.setTestedModelName(version.getTestedModelName());
        response.setTestErrorCode(version.getTestErrorCode());
        response.setTestCandidateCount(version.getTestCandidateCount());
        response.setTestWorkCount(version.getTestWorkCount());
        response.setTestedBy(version.getTestedBy());
        response.setTestedAt(version.getTestedAt());
        response.setReleasedBy(version.getReleasedBy());
        response.setReleasedAt(version.getReleasedAt());
        response.setUpdateUserId(version.getUpdateUserId());
        response.setUpdateUserName(version.getUpdateUserName());
        response.setLastUpdate(version.getLastUpdate());
        response.setVersion(version.getVersion());
    }

    private static ProfileImportPromptAuditRespDTO toAuditResponse(
            AiProfileImportPromptAudit audit) {
        ProfileImportPromptAuditRespDTO response = new ProfileImportPromptAuditRespDTO();
        response.setPromptAuditId(audit.getPromptAuditId());
        response.setTemplateId(audit.getTemplateId());
        response.setPromptVersionId(audit.getPromptVersionId());
        response.setActionCode(audit.getActionCode());
        response.setFromVersionId(audit.getFromVersionId());
        response.setToVersionId(audit.getToVersionId());
        response.setContentSha256(audit.getContentSha256());
        response.setRuntimeSha256(audit.getRuntimeSha256());
        response.setSchemaVersion(audit.getSchemaVersion());
        response.setContractVersion(audit.getContractVersion());
        response.setFixtureCode(audit.getFixtureCode());
        response.setFixtureVersion(audit.getFixtureVersion());
        response.setFixtureSha256(audit.getFixtureSha256());
        response.setModelName(audit.getModelName());
        response.setConfigVersion(audit.getConfigVersion());
        response.setTestOperatorId(audit.getTestOperatorId());
        response.setTestedAt(audit.getTestedAt());
        response.setOperatorId(audit.getOperatorId());
        response.setOperatorName(audit.getOperatorName());
        response.setReasonCode(audit.getReasonCode());
        response.setResultStatus(audit.getResultStatus());
        response.setErrorCode(audit.getErrorCode());
        response.setMessage(audit.getMessage());
        response.setCreateTime(audit.getCreateTime());
        return response;
    }

    private static BizException invalid() {
        return ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_INVALID.toException();
    }

    private static BizException stateConflict() {
        return ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_STATE_CONFLICT.toException();
    }

    private static BizException versionConflict() {
        return ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_VERSION_CONFLICT.toException();
    }

    private static IllegalStateException affectedRowsFailure() {
        return new IllegalStateException("Prompt management write did not affect exactly one row");
    }
}
