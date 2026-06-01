package com.kaipai.controller.admin.content;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.model.card.dto.TemplateCreateDTO;
import com.kaipai.model.card.dto.TemplateDetailDTO;
import com.kaipai.model.card.dto.TemplateItemDTO;
import com.kaipai.model.card.dto.TemplateListQueryDTO;
import com.kaipai.model.card.dto.TemplatePublishDTO;
import com.kaipai.model.card.dto.TemplatePublishLogItemDTO;
import com.kaipai.model.card.dto.TemplatePublishLogQueryDTO;
import com.kaipai.model.card.dto.TemplateRollbackDTO;
import com.kaipai.model.card.dto.TemplateSortDTO;
import com.kaipai.model.card.dto.TemplateStatusChangeDTO;
import com.kaipai.model.card.dto.TemplateUpdateDTO;
import com.kaipai.model.card.dto.ContactRequestDecisionDTO;
import com.kaipai.model.card.dto.ThemeTokenItemDTO;
import com.kaipai.model.card.dto.ThemeTokenQueryDTO;
import com.kaipai.model.card.dto.ThemeTokenUpdateDTO;
import com.kaipai.model.card.dto.ShareArtifactItemDTO;
import com.kaipai.model.card.dto.ShareArtifactQueryDTO;
import com.kaipai.model.card.dto.ShareArtifactUpdateDTO;
import com.kaipai.model.card.dto.AdminContactRequestDetailDTO;
import com.kaipai.model.card.dto.AdminContactRequestItemDTO;
import com.kaipai.model.card.dto.AdminContactRequestQueryDTO;
import com.kaipai.model.card.dto.AdminShareCardGovernanceDetailDTO;
import com.kaipai.model.card.dto.AdminShareCardGovernanceItemDTO;
import com.kaipai.model.card.dto.AdminShareCardGovernanceQueryDTO;
import com.kaipai.service.card.CardSceneTemplateService;
import com.kaipai.service.card.ShareCardContactRequestService;
import com.kaipai.service.card.TemplatePublishLogService;
import com.kaipai.service.card.UserShareCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台内容配置")
@RestController
@RequestMapping("/admin/content")
@RequiredArgsConstructor
public class AdminContentController {

    private final CardSceneTemplateService templateService;
    private final TemplatePublishLogService templatePublishLogService;
    private final ShareCardContactRequestService shareCardContactRequestService;
    private final UserShareCardService userShareCardService;

    @Operation(summary = "模板列表")
    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('page.content.templates')")
    public R<PageResult<TemplateItemDTO>> list(@Valid TemplateListQueryDTO queryDTO) {
        return R.ok(templateService.adminTemplateList(queryDTO));
    }

    @Operation(summary = "模板详情")
    @GetMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('page.content.templates')")
    public R<TemplateDetailDTO> detail(@PathVariable Long id) {
        return R.ok(templateService.adminTemplateDetail(id));
    }

    @Operation(summary = "新建模板")
    @PostMapping("/templates")
    @PreAuthorize("hasAuthority('action.content.template.create')")
    public R<Void> create(@Valid @RequestBody TemplateCreateDTO dto) {
        templateService.createTemplate(dto);
        return R.ok();
    }

    @Operation(summary = "更新模板")
    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('action.content.template.edit')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody TemplateUpdateDTO dto) {
        assertPathId(dto.getTemplateId(), id);
        dto.setTemplateId(id);
        templateService.updateTemplate(dto);
        return R.ok();
    }

    @Operation(summary = "启用模板")
    @PostMapping("/templates/{id}/enable")
    @PreAuthorize("hasAuthority('action.content.template.enable')")
    public R<Void> enable(@PathVariable Long id, @RequestBody(required = false) TemplateStatusChangeDTO dto) {
        templateService.enableTemplate(id, dto);
        return R.ok();
    }

    @Operation(summary = "禁用模板")
    @PostMapping("/templates/{id}/disable")
    @PreAuthorize("hasAuthority('action.content.template.disable')")
    public R<Void> disable(@PathVariable Long id, @RequestBody(required = false) TemplateStatusChangeDTO dto) {
        templateService.disableTemplate(id, dto);
        return R.ok();
    }

    @Operation(summary = "调整模板排序")
    @PostMapping("/templates/{id}/sort")
    @PreAuthorize("hasAuthority('action.content.template.sort')")
    public R<Void> sort(@PathVariable Long id, @Valid @RequestBody TemplateSortDTO dto) {
        templateService.sortTemplate(id, dto);
        return R.ok();
    }

    @Operation(summary = "发布模板")
    @PostMapping("/templates/{id}/publish")
    @PreAuthorize("hasAuthority('action.content.template.publish')")
    public R<Void> publish(@PathVariable Long id, @Valid @RequestBody TemplatePublishDTO dto) {
        assertPathId(dto.getTemplateId(), id);
        dto.setTemplateId(id);
        templateService.publishTemplate(dto);
        return R.ok();
    }

    @Operation(summary = "回滚模板")
    @PostMapping("/templates/{id}/rollback")
    @PreAuthorize("hasAuthority('action.content.template.rollback')")
    public R<Void> rollback(@PathVariable Long id, @Valid @RequestBody TemplateRollbackDTO dto) {
        assertPathId(dto.getTemplateId(), id);
        dto.setTemplateId(id);
        templateService.rollbackTemplate(dto);
        return R.ok();
    }

    private void assertPathId(Long bodyTemplateId, Long pathTemplateId) {
        if (bodyTemplateId != null && !bodyTemplateId.equals(pathTemplateId)) {
            throw new BizException("templateId 与路径 id 不一致");
        }
    }

    @Operation(summary = "发布记录列表")
    @GetMapping("/publish-logs")
    @PreAuthorize("hasAuthority('page.content.publish-logs')")
    public R<PageResult<TemplatePublishLogItemDTO>> publishLogs(@Valid TemplatePublishLogQueryDTO queryDTO) {
        return R.ok(templatePublishLogService.adminPublishLogList(queryDTO));
    }

    @Operation(summary = "主题 token 列表")
    @GetMapping("/theme-tokens")
    @PreAuthorize("hasAuthority('page.content.theme-tokens')")
    public R<PageResult<ThemeTokenItemDTO>> themeTokens(@Valid ThemeTokenQueryDTO queryDTO) {
        return R.ok(templateService.adminThemeTokenList(queryDTO));
    }

    @Operation(summary = "更新主题 token")
    @PutMapping("/theme-tokens/{templateId}")
    @PreAuthorize("hasAuthority('action.content.theme.edit')")
    public R<Void> updateThemeTokens(@PathVariable Long templateId, @Valid @RequestBody ThemeTokenUpdateDTO dto) {
        templateService.updateThemeToken(templateId, dto);
        return R.ok();
    }

    @Operation(summary = "分享产物配置列表")
    @GetMapping("/share-artifacts")
    @PreAuthorize("hasAuthority('page.content.share-artifacts')")
    public R<PageResult<ShareArtifactItemDTO>> shareArtifacts(@Valid ShareArtifactQueryDTO queryDTO) {
        return R.ok(templateService.adminShareArtifactList(queryDTO));
    }

    @Operation(summary = "更新分享产物配置")
    @PutMapping("/share-artifacts/{templateId}")
    @PreAuthorize("hasAuthority('action.content.artifact.edit')")
    public R<Void> updateShareArtifacts(@PathVariable Long templateId, @Valid @RequestBody ShareArtifactUpdateDTO dto) {
        templateService.updateShareArtifact(templateId, dto);
        return R.ok();
    }

    @Operation(summary = "联系方式申请记录列表")
    @GetMapping("/contact-requests")
    @PreAuthorize("hasAuthority('page.content.contact-requests')")
    public R<PageResult<AdminContactRequestItemDTO>> contactRequests(@Valid AdminContactRequestQueryDTO queryDTO) {
        return R.ok(shareCardContactRequestService.adminContactRequestList(queryDTO));
    }

    @Operation(summary = "联系方式申请记录详情")
    @GetMapping("/contact-requests/{requestId}")
    @PreAuthorize("hasAuthority('page.content.contact-requests')")
    public R<AdminContactRequestDetailDTO> contactRequestDetail(@PathVariable Long requestId) {
        return R.ok(shareCardContactRequestService.adminContactRequestDetail(requestId));
    }

    @Operation(summary = "后台同意联系方式申请")
    @PostMapping("/contact-requests/{requestId}/approve")
    @PreAuthorize("hasAuthority('action.content.contact-request.approve')")
    public R<Void> approveContactRequest(@PathVariable Long requestId,
                                          @RequestBody(required = false) ContactRequestDecisionDTO dto) {
        shareCardContactRequestService.adminApprove(requestId, dto);
        return R.ok();
    }

    @Operation(summary = "后台拒绝联系方式申请")
    @PostMapping("/contact-requests/{requestId}/reject")
    @PreAuthorize("hasAuthority('action.content.contact-request.reject')")
    public R<Void> rejectContactRequest(@PathVariable Long requestId,
                                         @RequestBody(required = false) ContactRequestDecisionDTO dto) {
        shareCardContactRequestService.adminReject(requestId, dto);
        return R.ok();
    }

    @Operation(summary = "分享卡治理列表")
    @GetMapping("/share-cards")
    @PreAuthorize("hasAuthority('page.content.share-cards')")
    public R<PageResult<AdminShareCardGovernanceItemDTO>> shareCards(@Valid AdminShareCardGovernanceQueryDTO queryDTO) {
        return R.ok(userShareCardService.adminShareCardList(queryDTO));
    }

    @Operation(summary = "分享卡治理详情")
    @GetMapping("/share-cards/{shareCardId}")
    @PreAuthorize("hasAuthority('page.content.share-cards')")
    public R<AdminShareCardGovernanceDetailDTO> shareCardDetail(@PathVariable Long shareCardId) {
        return R.ok(userShareCardService.adminShareCardDetail(shareCardId));
    }
}

