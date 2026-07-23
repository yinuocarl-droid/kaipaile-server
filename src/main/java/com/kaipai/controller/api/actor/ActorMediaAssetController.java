package com.kaipai.controller.api.actor;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.R;
import com.kaipai.model.actor.dto.*;
import com.kaipai.service.actor.ActorMediaAssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/actor/assets")
@RequiredArgsConstructor
public class ActorMediaAssetController {
    private final ActorMediaAssetService actorMediaAssetService;

    @GetMapping
    public R<PageResult<ActorAssetRespDTO>> list(Authentication auth, @ModelAttribute ActorAssetQueryDTO query) {
        return R.ok(actorMediaAssetService.list(userId(auth), query));
    }

    @GetMapping("/{id}")
    public R<ActorAssetRespDTO> get(Authentication auth, @PathVariable Long id) {
        return R.ok(actorMediaAssetService.asset(userId(auth), id));
    }

    @PostMapping
    public R<ActorAssetRespDTO> create(Authentication auth,
            @RequestParam String mediaType,
            @RequestParam(required = false) String categoryCode,
            @RequestParam("file") MultipartFile file) {
        return R.ok(actorMediaAssetService.upload(userId(auth), mediaType, categoryCode, file));
    }

    @PutMapping("/{id}")
    public R<ActorAssetRespDTO> update(Authentication auth, @PathVariable Long id, @RequestBody ActorAssetUpdateDTO request) {
        return R.ok(actorMediaAssetService.update(userId(auth), id, request));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(Authentication auth, @PathVariable Long id) {
        actorMediaAssetService.delete(userId(auth), id);
        return R.ok();
    }

    @PutMapping("/current-resume")
    public R<Void> setCurrentResume(Authentication auth, @Valid @RequestBody ActorCurrentResumeUpdateDTO request) {
        actorMediaAssetService.setCurrentResume(userId(auth), request);
        return R.ok();
    }

    @PostMapping("/{id}/access-url")
    public R<ActorAssetAccessUrlRespDTO> accessUrl(Authentication auth, @PathVariable Long id) {
        return R.ok(actorMediaAssetService.issueOwnerAccessUrl(userId(auth), id));
    }

    private Long userId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long id)) {
            throw new BizException("未登录或登录态失效");
        }
        return id;
    }
}
