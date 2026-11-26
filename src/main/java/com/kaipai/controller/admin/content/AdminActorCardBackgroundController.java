package com.kaipai.controller.admin.content;
import com.kaipai.common.result.R;
import com.kaipai.model.actor.card.dto.AdminActorCardBackgroundDTO.*;
import com.kaipai.service.actor.AdminActorCardBackgroundService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Public catalog administration under existing template-page permissions. */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/admin/content")
public class AdminActorCardBackgroundController {
    private final AdminActorCardBackgroundService service;
    @GetMapping("/actor-card-backgrounds")
    @PreAuthorize("hasAuthority('page.content.templates')")
    public R<List<Item>> list(@RequestParam(required=false) String style, @RequestParam(defaultValue="true") boolean includeDisabled,
            @RequestParam(defaultValue="1") @Min(1) int page, @RequestParam(defaultValue="30") @Min(1) @Max(100) int size) {
        return R.ok(service.list(style, includeDisabled, page, size));
    }
    @PostMapping(value="/actor-card-backgrounds", consumes="multipart/form-data")
    @PreAuthorize("hasAuthority('action.content.template.edit')")
    public R<Item> upload(@RequestPart MultipartFile file, @RequestParam String style, @RequestParam(defaultValue="0") int sortOrder) {
        return R.ok(service.upload(file, style, sortOrder));
    }
    @PatchMapping("/actor-card-backgrounds/{id}")
    @PreAuthorize("hasAuthority('action.content.template.edit')")
    public R<Item> update(@PathVariable @Positive Long id, @Valid @RequestBody UpdateReq req) { return R.ok(service.update(id, req)); }
    @GetMapping("/templates/{id}/decoration/editor-background")
    @PreAuthorize("hasAuthority('page.content.templates')")
    public R<ContextResp> context(@PathVariable @Positive Long id) { return R.ok(service.editorBackground(id)); }
    @PutMapping("/templates/{id}/decoration/editor-background")
    @PreAuthorize("hasAuthority('action.content.template.edit')")
    public R<ContextResp> saveContext(@PathVariable @Positive Long id, @Valid @RequestBody ContextSaveReq req) { return R.ok(service.saveEditorBackground(id, req)); }
}
