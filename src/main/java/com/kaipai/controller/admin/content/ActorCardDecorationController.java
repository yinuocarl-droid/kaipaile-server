package com.kaipai.controller.admin.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.kaipai.common.result.R;
import com.kaipai.model.actor.card.dto.ActorCardDecorationDTO.*;
import com.kaipai.service.actor.ActorCardDecorationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Admin-only decoration lifecycle, deliberately separate from legacy template status actions. */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/admin/content/templates/{id}/decoration")
public class ActorCardDecorationController {
    private final ActorCardDecorationService service;

    @GetMapping("/draft")
    @PreAuthorize("hasAuthority('page.content.templates')")
    public R<DraftResp> draft(@PathVariable @Positive Long id) { return R.ok(service.load(id)); }

    @PutMapping("/draft")
    @PreAuthorize("hasAuthority('action.content.template.edit')")
    public R<DraftResp> save(@PathVariable @Positive Long id, @Valid @RequestBody SaveReq req) { return R.ok(service.save(id, req)); }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('page.content.templates')")
    public R<JsonNode> preview(@PathVariable @Positive Long id, @Valid @RequestBody PreviewReq req) { return R.ok(service.preview(id, req.config())); }

    @PostMapping("/publish")
    @PreAuthorize("hasAuthority('action.content.template.publish')")
    public R<DraftResp> publish(@PathVariable @Positive Long id, @Valid @RequestBody ChangeReq req) { return R.ok(service.publish(id, req)); }

    @GetMapping("/versions")
    @PreAuthorize("hasAuthority('page.content.templates')")
    public R<List<VersionItem>> versions(@PathVariable @Positive Long id) { return R.ok(service.versions(id)); }

    @GetMapping("/versions/{version}")
    @PreAuthorize("hasAuthority('page.content.templates')")
    public R<VersionResp> version(@PathVariable @Positive Long id, @PathVariable @Positive int version) { return R.ok(service.version(id, version)); }

    @PostMapping("/activate/{version}")
    @PreAuthorize("hasAuthority('action.content.template.rollback')")
    public R<DraftResp> activate(@PathVariable @Positive Long id, @PathVariable @Positive int version,
                                  @Valid @RequestBody ChangeReq req) { return R.ok(service.activate(id, version, req)); }
}
