package com.kaipai.controller.api.card;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.model.card.dto.ActorCardConfigRespDTO;
import com.kaipai.model.card.dto.ActorMyShareCardsRespDTO;
import com.kaipai.model.card.dto.ActorCardConfigSaveDTO;
import com.kaipai.model.card.dto.ActorCardConfigQueryDTO;
import com.kaipai.model.card.dto.ActorPersonalizationRespDTO;
import com.kaipai.model.card.dto.ActorPersonalizationQueryDTO;
import com.kaipai.model.card.dto.ActorSceneTemplateRespDTO;
import com.kaipai.model.card.dto.ActorMyShareCardItemDTO;
import com.kaipai.model.card.dto.CreateShareCardDTO;
import com.kaipai.service.card.ActorCardConfigService;
import com.kaipai.service.card.ActorPersonalizationService;
import com.kaipai.service.card.ActorSharePreferenceService;
import com.kaipai.service.card.CardSceneTemplateService;
import com.kaipai.service.card.TemplatePublishLogService;
import com.kaipai.service.card.UserShareCardService;
import com.kaipai.service.card.ShareCardFavoriteService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.card.dto.ShareCardFavoriteItemDTO;
import com.kaipai.model.card.dto.ShareCardFavoriteStateDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;

@Tag(name = "场景模板配置")
@RestController
@RequestMapping("/card")
@RequiredArgsConstructor
public class CardController {

    private final CardSceneTemplateService templateService;
    private final ActorCardConfigService cardConfigService;
    private final ActorPersonalizationService actorPersonalizationService;
    private final ActorSharePreferenceService sharePreferenceService;
    private final TemplatePublishLogService publishLogService;
    private final UserShareCardService userShareCardService;
    private final ShareCardFavoriteService shareCardFavoriteService;

    @GetMapping("/favorites")
    public R<PageResult<ShareCardFavoriteItemDTO>> favorites(Authentication authentication, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return R.ok(shareCardFavoriteService.list(currentUserId(authentication), page, size));
    }

    @PutMapping("/{shareCardId}/favorite")
    public R<ShareCardFavoriteStateDTO> favorite(Authentication authentication, @PathVariable Long shareCardId) {
        return R.ok(shareCardFavoriteService.add(currentUserId(authentication), shareCardId));
    }

    @DeleteMapping("/{shareCardId}/favorite")
    public R<ShareCardFavoriteStateDTO> unfavorite(Authentication authentication, @PathVariable Long shareCardId) {
        return R.ok(shareCardFavoriteService.remove(currentUserId(authentication), shareCardId));
    }

    @Operation(summary = "获取演员端场景模板列表")
    @GetMapping("/scene-templates")
    public R<List<ActorSceneTemplateRespDTO>> actorTemplates() {
        return R.ok(templateService.actorSceneTemplates());
    }

    @Operation(summary = "获取开拍了名片配置")
    @GetMapping("/config")
    public R<ActorCardConfigRespDTO> config(@Valid @ModelAttribute ActorCardConfigQueryDTO query) {
        return R.ok(cardConfigService.actorConfig(query.getShareCardId()));
    }

    @Operation(summary = "获取我的分享卡片列表")
    @GetMapping("/my-cards")
    public R<ActorMyShareCardsRespDTO> myCards(Authentication authentication) {
        return R.ok(cardConfigService.myCards(currentUserId(authentication)));
    }

    @Operation(summary = "创建我的分享卡片")
    @PostMapping("/my-cards")
    public R<ActorMyShareCardItemDTO> createMyCard(Authentication authentication,
                                                   @Valid @RequestBody CreateShareCardDTO dto) {
        return R.ok(userShareCardService.createCard(currentUserId(authentication), dto));
    }

    @Operation(summary = "归档我的分享卡片")
    @PostMapping("/my-cards/{cardId}/archive")
    public R<Void> archiveMyCard(Authentication authentication,
                                 @PathVariable Long cardId) {
        userShareCardService.archiveCard(currentUserId(authentication), cardId);
        return R.ok();
    }

    @Operation(summary = "获取演员端个性化汇总")
    @GetMapping("/personalization")
    public R<ActorPersonalizationRespDTO> personalization(Authentication authentication,
                                                          @Valid @ModelAttribute ActorPersonalizationQueryDTO query) {
        return R.ok(actorPersonalizationService.resolve(query.getShareCardId()));
    }

    @Operation(summary = "保存开拍了名片配置")
    @PostMapping("/config")
    public R<ActorCardConfigRespDTO> saveConfig(Authentication authentication,
                                                @Valid @RequestBody ActorCardConfigSaveDTO dto) {
        return R.ok(cardConfigService.saveActorConfig(currentUserId(authentication), dto));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BizException("未登录或登录态失效");
        }
        return userId;
    }

}
