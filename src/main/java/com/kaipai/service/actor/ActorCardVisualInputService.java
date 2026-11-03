package com.kaipai.service.actor;

import com.kaipai.common.exception.BizException;
import com.kaipai.integration.ai.provider.AiProfileImageProvider;
import com.kaipai.integration.ai.provider.AiProfileImageProviderRegistry;
import com.kaipai.mapper.actor.ActorCardBackgroundMapper;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardVisualInputMapper;
import com.kaipai.mapper.ai.ActorAiProfileCardTaskMapper;
import com.kaipai.mapper.user.UserMapper;
import com.kaipai.model.actor.card.dto.ActorCardVisualInputDTO.CapabilitiesResp;
import com.kaipai.model.actor.card.dto.ActorCardVisualInputDTO.Resp;
import com.kaipai.model.actor.card.dto.ActorCardVisualInputDTO.SaveReq;
import com.kaipai.model.actor.card.dto.ActorCardVisualInputDTO.TaskResp;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.actor.card.entity.ActorCardBackground;
import com.kaipai.model.actor.card.entity.ActorCardVisualInput;
import com.kaipai.model.ai.entity.ActorAiProfileCardTask;
import com.kaipai.model.user.entity.User;
import com.kaipai.service.ai.AiImageProviderConfigService;
import com.kaipai.service.ai.config.AiProfileCardProperties;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Validates and persists stable AI main-visual inputs independently from decoration layout. */
@Service
@RequiredArgsConstructor
public class ActorCardVisualInputService {

    public static final String MODE_PERSON_AI = "person_ai";
    public static final String MODE_PERSON_BACKGROUND_AI = "person_background_ai";
    public static final String GENERATION_CONFIG_VERSION = "actor-card-visual-v1";
    private static final Duration RESPONSE_READ_TTL = Duration.ofMinutes(10);

    private final ActorCardVisualInputMapper mapper;
    private final ActorCardMapper cardMapper;
    private final UserMapper userMapper;
    private final ActorMediaAssetService assetService;
    private final ActorCardBackgroundMapper backgroundMapper;
    private final AiGeneratedImageStorage imageStorage;
    private final ActorCardDecorationAssignmentService assignmentService;
    private final ActorAiProfileCardTaskMapper taskMapper;
    private final AiProfileImageProviderRegistry providerRegistry;
    private final AiImageProviderConfigService providerConfigService;
    private final AiProfileCardProperties properties;

    public Resp get(Long userId, Long cardId) {
        requireOwnedCard(userId, cardId);
        ActorCardVisualInput value;
        try {
            value = mapper.selectByCardId(cardId);
        } catch (RuntimeException error) {
            throw visualStorageFailure(error);
        }
        return value == null ? null : toResponse(value);
    }

    public CapabilitiesResp capabilities() {
        ProviderSelection selection = activeProvider();
        boolean dual = selection.provider().supportsReferenceImageCount(2);
        return new CapabilitiesResp(
                selection.providerCode(), selection.provider().modelCode(), true, dual,
                dual ? null : "当前 AI Provider 不支持独立人物图 + 背景图双参考输入");
    }

    @Transactional(rollbackFor = Exception.class)
    public Resp save(Long userId, Long cardId, SaveReq request) {
        ActorCard card = requireOwnedCardForUpdate(userId, cardId);
        if (!"draft".equals(card.getStatus())) {
            throw new BizException("只有草稿状态的演员卡可以编辑首图");
        }
        requireRealName(userId);
        if (request == null) {
            throw new BizException("首图输入不能为空");
        }
        String mode = normalizeMode(request.generationMode());
        long expected = request.expectedRevision() == null ? 0L : request.expectedRevision();
        ActorCardVisualInput current;
        try {
            current = mapper.selectByCardIdForUpdate(cardId);
        } catch (RuntimeException error) {
            throw visualStorageFailure(error);
        }
        if (current == null && expected != 0L) {
            throw revisionConflict();
        }
        if (current != null && current.getRevision() != expected) {
            throw revisionConflict();
        }
        validateProviderCapability(mode);

        boolean hasAsset = request.personAssetId() != null;
        boolean hasUrl = StringUtils.hasText(request.personImageUrl());
        if (hasAsset == hasUrl) {
            throw new BizException("personAssetId 与 personImageUrl 必须且只能填写一个");
        }

        String personSourceType;
        Long personAssetId = null;
        String personImageUrl = null;
        if (hasAsset) {
            assetService.requireOwnedReadyPhoto(userId, request.personAssetId());
            personSourceType = "asset";
            personAssetId = request.personAssetId();
        } else {
            personImageUrl = normalizeManagedPersonUrl(card, current, request.personImageUrl());
            personSourceType = "managed_url";
        }

        ActorCardBackground background = requireBackground(mode, request.backgroundId());
        String backgroundImageUrl = background == null ? null : background.getImageUrl().trim();
        String fingerprint = fingerprint(
                mode,
                personAssetId == null ? "url:" + personImageUrl : "asset:" + personAssetId,
                background == null ? "" : "background:" + background.getId() + ":" + backgroundImageUrl,
                GENERATION_CONFIG_VERSION);

        if (current != null && fingerprint.equals(current.getInputFingerprint())) {
            assignmentService.selectForMode(userId, cardId, mode, current.getBackgroundId());
            return toResponse(current);
        }

        ActorCardVisualInput value = new ActorCardVisualInput();
        value.setCardId(cardId);
        value.setUserId(userId);
        value.setGenerationMode(mode);
        value.setPersonSourceType(personSourceType);
        value.setPersonAssetId(personAssetId);
        value.setPersonImageUrl(personImageUrl);
        value.setBackgroundId(background == null ? null : background.getId());
        value.setBackgroundImageUrl(backgroundImageUrl);
        value.setRevision(expected + 1);
        value.setInputFingerprint(fingerprint);
        value.setGenerationConfigVersion(GENERATION_CONFIG_VERSION);

        int changed;
        try {
            changed = current == null ? mapper.insert(value) : mapper.updateInputCas(value, expected);
        } catch (RuntimeException error) {
            throw visualStorageFailure(error);
        }
        if (changed != 1) {
            throw revisionConflict();
        }
        assignmentService.selectForMode(userId, cardId, mode, value.getBackgroundId());
        return toResponse(value);
    }

    private Resp toResponse(ActorCardVisualInput value) {
        String personUrl = responsePersonUrl(value);
        ActorAiProfileCardTask task = taskMapper.selectByCardModeAndSource(
                value.getCardId(), value.getGenerationMode(), value.getInputFingerprint());
        String stableMainVisual = value.getInputFingerprint().equals(value.getGeneratedFingerprint())
                ? value.getGeneratedImageUrl()
                : null;
        String mainVisual = responseGeneratedUrl(stableMainVisual);
        String taskImage = task == null ? null : task.getGeneratedImageUrl();
        String taskReadUrl = StringUtils.hasText(taskImage) && taskImage.equals(stableMainVisual)
                ? mainVisual : responseGeneratedUrl(taskImage);
        TaskResp taskResp = task == null ? null : new TaskResp(
                task.getTaskId(), responseStatus(task.getStatus()), taskReadUrl, task.getFailureReason());
        return new Resp(
                value.getCardId(), value.getGenerationMode(), value.getPersonSourceType(),
                value.getPersonAssetId(), personUrl,
                "managed_url".equals(value.getPersonSourceType()) ? value.getPersonImageUrl() : null,
                value.getBackgroundId(),
                value.getRevision(), value.getInputFingerprint(), mainVisual, taskResp);
    }

    private String responseGeneratedUrl(String stableUrl) {
        return StringUtils.hasText(stableUrl)
                ? imageStorage.issueReadUrl(stableUrl, RESPONSE_READ_TTL) : null;
    }

    private String responsePersonUrl(ActorCardVisualInput value) {
        if (value.getPersonAssetId() != null) {
            var access = assetService.issueOwnerReadyPhotoAccessUrl(value.getUserId(), value.getPersonAssetId());
            return access == null ? null : access.getAccessUrl();
        }
        return StringUtils.hasText(value.getPersonImageUrl())
                ? imageStorage.issueReadUrl(value.getPersonImageUrl(), RESPONSE_READ_TTL)
                : null;
    }

    private String responseStatus(String status) {
        if ("running".equals(status)) {
            return "processing";
        }
        if ("success".equals(status)) {
            return "success";
        }
        return status;
    }

    private ActorCard requireOwnedCard(Long userId, Long cardId) {
        ActorCard card = cardMapper.selectById(cardId);
        if (card == null) {
            throw new BizException("演员卡不存在");
        }
        if (!userId.equals(card.getUserId())) {
            throw new BizException("无权操作该演员卡");
        }
        return card;
    }

    /**
     * Serialize creation of the one-per-card visual row on its existing parent.
     * Locking only a missing visual row is a gap lock under MySQL RR and lets two
     * first writers deadlock while upgrading their locks to INSERT.
     */
    private ActorCard requireOwnedCardForUpdate(Long userId, Long cardId) {
        ActorCard locked = cardMapper.selectOwnedForUpdate(userId, cardId);
        if (locked == null) {
            throw new BizException("演员卡不存在或无权操作");
        }
        return locked;
    }

    private void requireRealName(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(2).equals(user.getRealAuthStatus())) {
            throw new BizException("完成实名认证后才可生成 AI 首图");
        }
    }

    private String normalizeMode(String mode) {
        String normalized = StringUtils.hasText(mode) ? mode.trim().toLowerCase() : "";
        if (!MODE_PERSON_AI.equals(normalized) && !MODE_PERSON_BACKGROUND_AI.equals(normalized)) {
            throw new BizException("generationMode 仅支持 person_ai 或 person_background_ai");
        }
        return normalized;
    }

    private void validateProviderCapability(String mode) {
        if (!MODE_PERSON_BACKGROUND_AI.equals(mode)) {
            return;
        }
        ProviderSelection selection = activeProvider();
        if (!selection.provider().supportsReferenceImageCount(2)) {
            throw new BizException("当前 AI Provider " + selection.providerCode()
                    + " 不支持人物图 + 背景图双参考图输入");
        }
    }

    private ProviderSelection activeProvider() {
        String code = providerConfigService.resolveActiveProviderCode(properties.getProviderCode());
        return new ProviderSelection(code, providerRegistry.resolve(code));
    }

    private ActorCardBackground requireBackground(String mode, Long backgroundId) {
        if (MODE_PERSON_AI.equals(mode)) {
            if (backgroundId != null) {
                throw new BizException("person_ai 模式不能携带 backgroundId");
            }
            return null;
        }
        if (backgroundId == null) {
            throw new BizException("person_background_ai 模式必须选择背景");
        }
        ActorCardBackground background = backgroundMapper.selectById(backgroundId);
        if (background == null || !Integer.valueOf(1).equals(background.getEnabled())
                || !StringUtils.hasText(background.getImageUrl())) {
            throw new BizException("所选背景不存在或已停用");
        }
        return background;
    }

    private String normalizeManagedPersonUrl(
            ActorCard card,
            ActorCardVisualInput current,
            String value) {
        String normalized = value.trim();
        boolean currentStableVisualSource = current != null
                && card.getUserId().equals(current.getUserId())
                && "managed_url".equals(current.getPersonSourceType())
                && StringUtils.hasText(current.getPersonImageUrl())
                && normalized.equals(current.getPersonImageUrl().trim())
                && isStableManagedUrl(normalized);
        if (!currentStableVisualSource) {
            throw new BizException("新的人物图片必须使用已验证归属的 personAssetId；"
                    + "personImageUrl 仅可回传当前首图输入的稳定地址");
        }
        return normalized;
    }

    private boolean isStableManagedUrl(String value) {
        if (!imageStorage.isManagedUrl(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return uri.getRawQuery() == null
                    && uri.getRawFragment() == null
                    && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private BizException revisionConflict() {
        return new BizException(409, "首图输入版本冲突，请重新读取后再保存");
    }

    private RuntimeException visualStorageFailure(RuntimeException error) {
        if (hasCause(error, DuplicateKeyException.class)) {
            return revisionConflict();
        }
        if (com.kaipai.service.actor.support.ActorCardVisualInputReadSupport
                .isMissingVisualInputTable(error)) {
            return new BizException(503,
                    "首图输入存储尚未就绪，请执行V20261022_003；未保存任何成功结果");
        }
        return error;
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable cursor = error;
        while (cursor != null) {
            if (type.isInstance(cursor)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    static String fingerprint(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private record ProviderSelection(String providerCode, AiProfileImageProvider provider) { }
}
