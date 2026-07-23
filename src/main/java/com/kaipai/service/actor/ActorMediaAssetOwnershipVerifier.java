package com.kaipai.service.actor;

public interface ActorMediaAssetOwnershipVerifier {
    void requireOwnedReadyPhoto(Long userId, Long assetId);
}
