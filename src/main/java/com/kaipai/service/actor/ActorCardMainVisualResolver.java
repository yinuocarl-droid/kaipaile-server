package com.kaipai.service.actor;

import com.kaipai.model.actor.card.dto.ActorCardMediaBindingRespDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Predicate;
import org.springframework.util.StringUtils;

/**
 * 演员卡单一首图解析器。数据库历史字段继续保留，所有读取侧通过同一优先级消费。
 */
public final class ActorCardMainVisualResolver {

    private ActorCardMainVisualResolver() {
    }

    public static boolean hasItems(ActorCardMediaBindingRespDTO binding) {
        return binding != null && binding.getItems() != null && !binding.getItems().isEmpty();
    }

    public static boolean hasAssetItem(ActorCardMediaBindingRespDTO binding) {
        return hasItems(binding) && binding.getItems().stream().anyMatch(item -> item.getAssetId() != null);
    }

    public static boolean hasExplicitEmptyBinding(ActorCardMediaBindingRespDTO binding) {
        return binding != null && binding.getSetId() != null && !hasItems(binding);
    }

    public static boolean hasEffectiveSource(ActorCard card, ActorCardMediaBindingRespDTO binding) {
        if (resolveExpandedImageUrl(card, binding) != null || hasItems(binding)) {
            return true;
        }
        if (hasExplicitEmptyBinding(binding)) {
            return StringUtils.hasText(card.getBackgroundImageUrl());
        }
        return StringUtils.hasText(card.getSourceImageUrl())
                || StringUtils.hasText(card.getBackgroundImageUrl());
    }

    /**
     * 生成任务不能持久化素材私有桶的短时签名 URL，asset 绑定由调用方以 null 表示。
     */
    public static String resolveGenerateInput(ActorCard card, ActorCardMediaBindingRespDTO binding) {
        String expandedImageUrl = resolveExpandedImageUrl(card, binding);
        if (expandedImageUrl != null) {
            return expandedImageUrl;
        }
        if (hasAssetItem(binding)) {
            return null;
        }
        String boundUrl = firstBindingUrl(binding, StringUtils::hasText);
        if (StringUtils.hasText(boundUrl)) {
            return boundUrl.trim();
        }
        if (!hasExplicitEmptyBinding(binding) && StringUtils.hasText(card.getSourceImageUrl())) {
            return card.getSourceImageUrl().trim();
        }
        return StringUtils.hasText(card.getBackgroundImageUrl())
                ? card.getBackgroundImageUrl().trim() : null;
    }

    public static String resolveDisplayUrl(
            ActorCard card,
            ActorCardMediaBindingRespDTO binding,
            Predicate<String> usable,
            String... fallbacks) {
        String value = firstUsable(usable, resolveExpandedImageUrl(card, binding));
        if (value != null) {
            return value;
        }
        return resolveSourceDisplayUrl(card, binding, usable, fallbacks);
    }

    public static String resolveSourceDisplayUrl(
            ActorCard card,
            ActorCardMediaBindingRespDTO binding,
            Predicate<String> usable,
            String... fallbacks) {
        String value = firstUsable(usable, firstBindingUrl(binding, usable));
        if (value != null) {
            return value;
        }
        if (!hasExplicitEmptyBinding(binding)) {
            value = firstUsable(usable, card.getSourceImageUrl());
            if (value != null) {
                return value;
            }
        }
        value = firstUsable(usable, card.getBackgroundImageUrl(), card.getGeneratedPreviewUrl());
        return value != null ? value : firstUsable(usable, fallbacks);
    }

    public static SourceReference resolveSourceReference(
            ActorCard card,
            ActorCardMediaBindingRespDTO binding) {
        if (hasItems(binding)) {
            var assetItem = binding.getItems().stream()
                    .filter(item -> item.getAssetId() != null)
                    .findFirst()
                    .orElse(null);
            if (assetItem != null) {
                return SourceReference.forAsset(assetItem.getAssetId());
            }
            String boundUrl = firstBindingUrl(binding, StringUtils::hasText);
            if (StringUtils.hasText(boundUrl)) {
                return SourceReference.forUrl(boundUrl);
            }
        }
        if (!hasExplicitEmptyBinding(binding) && StringUtils.hasText(card.getSourceImageUrl())) {
            return SourceReference.forUrl(card.getSourceImageUrl());
        }
        return StringUtils.hasText(card.getBackgroundImageUrl())
                ? SourceReference.forUrl(card.getBackgroundImageUrl()) : null;
    }

    public static String resolveExpandedImageUrl(
            ActorCard card,
            ActorCardMediaBindingRespDTO binding) {
        if (!StringUtils.hasText(card.getExpandedImageUrl())) {
            return null;
        }
        if (!StringUtils.hasText(card.getExpandedSourceFingerprint())) {
            return card.getExpandedImageUrl().trim();
        }
        SourceReference current = resolveSourceReference(card, binding);
        return current != null
                && card.getExpandedSourceFingerprint().trim().equals(current.fingerprint())
                ? card.getExpandedImageUrl().trim() : null;
    }

    public record SourceReference(Long assetId, String imageUrl, String fingerprint) {

        public static SourceReference forAsset(Long assetId) {
            if (assetId == null) {
                throw new IllegalArgumentException("assetId is required");
            }
            return new SourceReference(assetId, null, fingerprint("asset:" + assetId));
        }

        public static SourceReference forUrl(String imageUrl) {
            if (!StringUtils.hasText(imageUrl)) {
                throw new IllegalArgumentException("imageUrl is required");
            }
            String normalized = imageUrl.trim();
            return new SourceReference(null, normalized, fingerprint("url:" + normalized));
        }

        private static String fingerprint(String value) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (NoSuchAlgorithmException error) {
                throw new IllegalStateException("SHA-256 is unavailable", error);
            }
        }
    }

    private static String firstBindingUrl(
            ActorCardMediaBindingRespDTO binding,
            Predicate<String> usable) {
        if (!hasItems(binding)) {
            return null;
        }
        return binding.getItems().stream()
                .map(item -> firstUsable(usable, item.getPreviewUrl(), item.getLegacyUrl()))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private static String firstUsable(Predicate<String> usable, String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value) && usable.test(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
