package com.kaipai.service.actor;
import com.kaipai.model.actor.dto.*;
public interface ActorMediaAssetService extends ActorMediaAssetOwnershipVerifier {
    ActorAssetRespDTO createReadyAsset(Long userId, String mediaType, String categoryCode, PrivateActorMediaStorage.StoredObjectRef object, String name, String mimeType, Long sizeBytes);
    ActorAssetAccessUrlRespDTO issueOwnerAccessUrl(Long userId, Long assetId);
    void delete(Long userId, Long assetId);
}
