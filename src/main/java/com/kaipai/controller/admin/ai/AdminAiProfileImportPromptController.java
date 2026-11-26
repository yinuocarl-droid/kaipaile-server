package com.kaipai.controller.admin.ai;

import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.result.R;
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
import com.kaipai.service.ai.ProfileImportPromptManagementService;
import com.kaipai.service.ai.profileimport.ProfileImportPromptReasonCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ai/profile-import/prompt-templates")
@RequiredArgsConstructor
public class AdminAiProfileImportPromptController {

    private final ProfileImportPromptManagementService service;
    private final AdminAuthContext authContext;

    @GetMapping
    @PreAuthorize("hasAuthority('page.system.ai-profile-import') and "
            + "hasAuthority('action.system.ai-profile-import.template-read')")
    public R<List<ProfileImportPromptTemplateSummaryRespDTO>> templates() {
        return R.ok(service.templates());
    }

    @GetMapping("/{templateCode}/versions")
    @PreAuthorize("hasAuthority('page.system.ai-profile-import') and "
            + "hasAuthority('action.system.ai-profile-import.template-read')")
    public R<List<ProfileImportPromptVersionSummaryRespDTO>> versions(
            @PathVariable String templateCode) {
        return R.ok(service.versions(templateCode));
    }

    @GetMapping("/versions/{versionId}")
    @PreAuthorize("hasAuthority('page.system.ai-profile-import') and "
            + "hasAuthority('action.system.ai-profile-import.template-read')")
    public R<ProfileImportPromptVersionDetailRespDTO> version(
            @PathVariable Long versionId) {
        return R.ok(service.version(versionId));
    }

    @PostMapping("/{templateCode}/drafts")
    @PreAuthorize("hasAuthority('page.system.ai-profile-import') and "
            + "hasAuthority('action.system.ai-profile-import.template-update')")
    public R<ProfileImportPromptTemplateSummaryRespDTO> createDraft(
            @PathVariable String templateCode,
            @RequestBody(required = false) ProfileImportPromptCreateDraftReqDTO request) {
        requireStrict(request);
        return R.ok(service.createDraft(currentAdminId(), templateCode, request));
    }

    @PutMapping("/versions/{versionId}")
    @PreAuthorize("hasAuthority('page.system.ai-profile-import') and "
            + "hasAuthority('action.system.ai-profile-import.template-update')")
    public R<ProfileImportPromptVersionDetailRespDTO> updateDraft(
            @PathVariable Long versionId,
            @RequestBody(required = false) ProfileImportPromptUpdateDraftReqDTO request) {
        requireStrict(request);
        return R.ok(service.updateDraft(currentAdminId(), versionId, request));
    }

    @PostMapping("/versions/{versionId}/abandon")
    @PreAuthorize("hasAuthority('page.system.ai-profile-import') and "
            + "hasAuthority('action.system.ai-profile-import.template-update')")
    public R<ProfileImportPromptTemplateSummaryRespDTO> abandonDraft(
            @PathVariable Long versionId,
            @RequestBody(required = false) ProfileImportPromptVersionActionReqDTO request) {
        requireStrict(request);
        ProfileImportPromptReasonCode.requireAbandon(request.getReasonCode());
        return R.ok(service.abandonDraft(currentAdminId(), versionId, request));
    }

    @PostMapping("/versions/{versionId}/test")
    @PreAuthorize("hasAuthority('page.system.ai-profile-import') and "
            + "hasAuthority('action.system.ai-profile-import.template-test')")
    public R<ProfileImportPromptTestResultRespDTO> test(@PathVariable Long versionId) {
        return R.ok(service.test(currentAdminId(), versionId));
    }

    @PostMapping("/versions/{versionId}/publish")
    @PreAuthorize("hasAuthority('page.system.ai-profile-import') and "
            + "hasAuthority('action.system.ai-profile-import.template-publish')")
    public R<ProfileImportPromptTemplateSummaryRespDTO> publish(
            @PathVariable Long versionId,
            @RequestBody(required = false) ProfileImportPromptVersionActionReqDTO request) {
        requireStrict(request);
        ProfileImportPromptReasonCode.requirePublish(request.getReasonCode());
        return R.ok(service.publish(currentAdminId(), versionId, request));
    }

    @PostMapping("/{templateCode}/versions/{versionId}/restore")
    @PreAuthorize("hasAuthority('page.system.ai-profile-import') and "
            + "hasAuthority('action.system.ai-profile-import.template-restore')")
    public R<ProfileImportPromptTemplateSummaryRespDTO> restore(
            @PathVariable String templateCode,
            @PathVariable Long versionId,
            @RequestBody(required = false) ProfileImportPromptRestoreReqDTO request) {
        requireStrict(request);
        ProfileImportPromptReasonCode.requireRestore(request.getReasonCode());
        return R.ok(service.restore(currentAdminId(), templateCode, versionId, request));
    }

    @GetMapping("/audits")
    @PreAuthorize("hasAuthority('page.system.ai-profile-import') and "
            + "hasAuthority('action.system.ai-profile-import.audit')")
    public R<List<ProfileImportPromptAuditRespDTO>> audits() {
        return R.ok(service.audits());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> invalidRequestBody(HttpMessageNotReadableException ignored) {
        ProfileDomainErrorCode error = ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_INVALID;
        return R.fail(error.code(), error.errorCode(), error.message());
    }

    private void requireStrict(ProfileImportPromptStrictWriteDTO request) {
        if (request == null) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_PROMPT_INVALID.toException();
        }
        request.requireNoUnexpectedFields();
    }

    private Long currentAdminId() {
        return authContext.requireCurrentAdmin().getAdminUserId();
    }
}
