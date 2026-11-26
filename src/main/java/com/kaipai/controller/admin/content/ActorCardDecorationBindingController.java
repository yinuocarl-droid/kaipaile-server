package com.kaipai.controller.admin.content;

import com.kaipai.common.result.R;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.BindingResp;
import com.kaipai.model.actor.card.dto.ActorCardDecorationAssignmentDTO.BindingSaveReq;
import com.kaipai.model.actor.card.dto.ActorCardPublicRespDTO;
import com.kaipai.service.actor.ActorCardDecorationAssignmentService;
import com.kaipai.service.actor.ActorCardPublicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Admin lifecycle for explicit default/background bindings and authorized real-card preview. */
@Tag(name = "后台演员卡装修绑定")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/admin/content/actor-card-decoration-bindings")
public class ActorCardDecorationBindingController {
    private final ActorCardDecorationAssignmentService assignmentService;
    private final ActorCardPublicService publicService;

    @GetMapping("/default")
    @PreAuthorize("hasAuthority('page.content.templates')")
    public R<BindingResp> defaultBinding() {
        return R.ok(assignmentService.getDefaultBinding());
    }

    @PutMapping("/default")
    @PreAuthorize("hasAuthority('action.content.template.publish')")
    public R<BindingResp> saveDefault(@Valid @RequestBody BindingSaveReq request) {
        return R.ok(assignmentService.saveDefaultBinding(request));
    }

    @GetMapping("/backgrounds/{backgroundId}")
    @PreAuthorize("hasAuthority('page.content.templates')")
    public R<BindingResp> backgroundBinding(@PathVariable @Positive Long backgroundId) {
        return R.ok(assignmentService.getBackgroundBinding(backgroundId));
    }

    @PutMapping("/backgrounds/{backgroundId}")
    @PreAuthorize("hasAuthority('action.content.template.publish')")
    public R<BindingResp> saveBackground(@PathVariable @Positive Long backgroundId,
                                         @Valid @RequestBody BindingSaveReq request) {
        return R.ok(assignmentService.saveBackgroundBinding(backgroundId, request));
    }

    @Operation(summary = "用当前已保存绑定预览真实卡片；不保存锁版且不触发AI")
    @GetMapping("/preview/cards/{cardId}")
    @PreAuthorize("hasAuthority('page.content.templates')")
    public R<ActorCardPublicRespDTO> preview(@PathVariable @Positive Long cardId,
                                             @RequestParam String generationMode,
                                             @RequestParam(required = false) Long backgroundId) {
        var decoration = assignmentService.previewForMode(cardId, generationMode, backgroundId);
        return R.ok(publicService.getAdminPreview(cardId, decoration));
    }
}
