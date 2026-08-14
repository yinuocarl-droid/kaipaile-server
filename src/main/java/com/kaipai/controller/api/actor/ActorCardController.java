package com.kaipai.controller.api.actor;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.model.actor.card.dto.ActorCardBackgroundLibraryRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardExpandImageReqDTO;
import com.kaipai.model.actor.card.dto.ActorCardExpandImageRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardGenerateRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardListItemDTO;
import com.kaipai.model.actor.card.dto.ActorCardPublicRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardStepSaveReqDTO;
import com.kaipai.model.actor.card.dto.ActorCardWorkRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardWorksReplaceReqDTO;
import com.kaipai.model.actor.card.dto.ActorProfileCompletenessRespDTO;
import com.kaipai.service.actor.ActorCardBackgroundService;
import com.kaipai.service.actor.ActorCardDraftService;
import com.kaipai.service.actor.ActorCardExpandImageService;
import com.kaipai.service.actor.ActorCardGenerateService;
import com.kaipai.service.actor.ActorCardPublicService;
import com.kaipai.service.actor.ActorCardPublishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "演员卡 - 草稿管理")
@RestController
@RequestMapping("/actor-card")
@RequiredArgsConstructor
public class ActorCardController {

    private final ActorCardDraftService actorCardDraftService;
    private final ActorCardBackgroundService backgroundService;
    private final ActorCardExpandImageService expandImageService;
    private final ActorCardGenerateService generateService;
    private final ActorCardPublishService publishService;
    private final ActorCardPublicService publicService;

    @Operation(summary = "新建演员卡草稿")
    @PostMapping("/draft")
    public R<ActorCardRespDTO> createDraft(Authentication authentication) {
        return R.ok(actorCardDraftService.createDraft(currentUserId(authentication)));
    }

    @Operation(summary = "按步骤自动保存草稿")
    @PutMapping("/draft/{cardId}/step")
    public R<Void> saveStep(Authentication authentication,
                            @PathVariable Long cardId,
                            @RequestBody ActorCardStepSaveReqDTO dto) {
        actorCardDraftService.saveStep(currentUserId(authentication), cardId, dto);
        return R.ok();
    }

    @Operation(summary = "读取草稿完整数据")
    @GetMapping("/draft/{cardId}")
    public R<ActorCardRespDTO> getDraft(Authentication authentication,
                                        @PathVariable Long cardId) {
        return R.ok(actorCardDraftService.getDraft(currentUserId(authentication), cardId));
    }

    @Operation(summary = "步骤3：整体替换参演作品快照")
    @PutMapping("/draft/{cardId}/works")
    public R<Void> replaceWorks(Authentication authentication,
                                @PathVariable Long cardId,
                                @Valid @RequestBody ActorCardWorksReplaceReqDTO dto) {
        actorCardDraftService.replaceWorks(currentUserId(authentication), cardId, dto);
        return R.ok();
    }

    @Operation(summary = "步骤3：读取已保存的参演作品快照")
    @GetMapping("/draft/{cardId}/works")
    public R<List<ActorCardWorkRespDTO>> listWorks(Authentication authentication,
                                                   @PathVariable Long cardId) {
        return R.ok(actorCardDraftService.listWorks(currentUserId(authentication), cardId));
    }

    @Operation(summary = "查询当前用户的草稿列表")
    @GetMapping("/drafts")
    public R<List<ActorCardRespDTO>> listDrafts(Authentication authentication) {
        return R.ok(actorCardDraftService.listDrafts(currentUserId(authentication)));
    }

    @Operation(summary = "删除草稿")
    @DeleteMapping("/draft/{cardId}")
    public R<Void> deleteDraft(Authentication authentication,
                                @PathVariable Long cardId) {
        actorCardDraftService.deleteDraft(currentUserId(authentication), cardId);
        return R.ok();
    }

    // ── T3: 背景图库 ───────────────────────────────────────────────────────────

    @Operation(summary = "按风格加载背景图库（classic|urban|ancient|fresh）")
    @GetMapping("/background-library")
    public R<ActorCardBackgroundLibraryRespDTO> backgroundLibrary(
            @RequestParam String style) {
        return R.ok(backgroundService.listByStyle(style));
    }

    // ── T4: AI 首图扩图 ────────────────────────────────────────────────────────

    @Operation(summary = "提交首图扩图任务（异步，返回 taskId）")
    @PostMapping("/draft/{cardId}/expand-image")
    public R<ActorCardExpandImageRespDTO> expandImage(
            Authentication authentication,
            @PathVariable Long cardId,
            @RequestBody ActorCardExpandImageReqDTO dto) {
        return R.ok(expandImageService.submit(currentUserId(authentication), cardId, dto));
    }

    @Operation(summary = "轮询扩图任务状态")
    @GetMapping("/draft/{cardId}/expand-image/{taskId}")
    public R<ActorCardExpandImageRespDTO> expandImageStatus(
            Authentication authentication,
            @PathVariable Long cardId,
            @PathVariable String taskId) {
        return R.ok(expandImageService.status(currentUserId(authentication), taskId));
    }

    // ── T5: AI 演员卡生成 ──────────────────────────────────────────────────────

    @Operation(summary = "提交演员卡 AI 生成任务（异步，返回 taskId）")
    @PostMapping("/draft/{cardId}/generate")
    public R<ActorCardGenerateRespDTO> generate(
            Authentication authentication,
            @PathVariable Long cardId) {
        return R.ok(generateService.submit(currentUserId(authentication), cardId));
    }

    @Operation(summary = "轮询演员卡生成任务状态")
    @GetMapping("/draft/{cardId}/generate/{taskId}")
    public R<ActorCardGenerateRespDTO> generateStatus(
            Authentication authentication,
            @PathVariable Long cardId,
            @PathVariable String taskId) {
        return R.ok(generateService.status(currentUserId(authentication), taskId));
    }

    // ── T6: 发布 / 名片夹列表 / 完整度 ─────────────────────────────────────────

    @Operation(summary = "发布演员卡")
    @PostMapping("/{cardId}/publish")
    public R<Void> publish(Authentication authentication,
                           @PathVariable Long cardId) {
        publishService.publish(currentUserId(authentication), cardId);
        return R.ok();
    }

    @Operation(summary = "名片夹列表（status=published|draft，不传则返回全部）")
    @GetMapping("/list")
    public R<List<ActorCardListItemDTO>> list(
            Authentication authentication,
            @RequestParam(required = false) String status) {
        return R.ok(publishService.list(currentUserId(authentication), status));
    }

    @Operation(summary = "个人中心：资料完整度与统计数字")
    @GetMapping("/profile/completeness")
    public R<ActorProfileCompletenessRespDTO> completeness(Authentication authentication) {
        return R.ok(publishService.completeness(currentUserId(authentication)));
    }

    // ── 00-215 / 00-218: 公开观看与复制创建 ─────────────────────────────────────

    @Operation(summary = "公开查看已发布演员卡（观看者分享落地，无需鉴权；草稿 403 / 不存在 404）")
    @GetMapping("/public/{cardId}")
    public R<ActorCardPublicRespDTO> publicView(@PathVariable Long cardId) {
        return R.ok(publicService.getPublicView(cardId));
    }

    @Operation(summary = "复制已发布演员卡为新草稿（含参演作品子表）")
    @PostMapping("/{cardId}/copy")
    public R<ActorCardRespDTO> copy(Authentication authentication,
                                    @PathVariable Long cardId) {
        return R.ok(publicService.copy(currentUserId(authentication), cardId));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }
}
