package com.kaipai.service.ai.profileimport;

import com.kaipai.common.exception.BizException;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import java.util.Set;
import org.springframework.util.StringUtils;

public final class ProfileImportSceneGuard {
    private static final Set<String> SUPPORTED = Set.of("full_profile", "works_only");

    private ProfileImportSceneGuard() {
    }

    public static String requireSupported(String scene) {
        if (!StringUtils.hasText(scene)) throw new BizException("scene 不能为空");
        String normalized = scene.trim();
        if (!SUPPORTED.contains(normalized)) throw new BizException("scene 不受支持");
        return normalized;
    }

    public static void requireMatches(String extractedScene, String applyScene) {
        String normalizedApplyScene = requireSupported(applyScene);
        if (!normalizedApplyScene.equals(extractedScene)) {
            throw new BizException(
                    ProfileDomainErrorCode.PROFILE_IMPORT_APPLY_CONFLICT.code(),
                    "智能导入场景不匹配");
        }
    }
}
