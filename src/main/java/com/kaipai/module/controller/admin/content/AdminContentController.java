package com.kaipai.module.controller.admin.content;

import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.module.model.card.dto.TemplateCreateDTO;
import com.kaipai.module.model.card.dto.TemplateItemDTO;
import com.kaipai.module.model.card.dto.TemplateListQueryDTO;
import com.kaipai.module.model.card.dto.TemplatePublishDTO;
import com.kaipai.module.model.card.dto.TemplateRollbackDTO;
import com.kaipai.module.model.card.dto.TemplateUpdateDTO;
import com.kaipai.module.server.card.service.CardSceneTemplateService;
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

    @Operation(summary = "模板列表")
    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('page.content.templates')")
    public R<PageResult<TemplateItemDTO>> list(@Valid TemplateListQueryDTO queryDTO) {
        return R.ok(templateService.adminTemplateList(queryDTO));
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
        dto.setTemplateId(id);
        templateService.updateTemplate(dto);
        return R.ok();
    }

    @Operation(summary = "发布模板")
    @PostMapping("/templates/{id}/publish")
    @PreAuthorize("hasAuthority('action.content.template.publish')")
    public R<Void> publish(@PathVariable Long id, @Valid @RequestBody TemplatePublishDTO dto) {
        dto.setTemplateId(id);
        templateService.publishTemplate(dto);
        return R.ok();
    }

    @Operation(summary = "回滚模板")
    @PostMapping("/templates/{id}/rollback")
    @PreAuthorize("hasAuthority('action.content.template.rollback')")
    public R<Void> rollback(@PathVariable Long id, @Valid @RequestBody TemplateRollbackDTO dto) {
        dto.setTemplateId(id);
        templateService.rollbackTemplate(dto);
        return R.ok();
    }
}
