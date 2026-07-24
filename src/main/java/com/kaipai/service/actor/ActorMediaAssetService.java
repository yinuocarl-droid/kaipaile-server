package com.kaipai.service.actor;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.actor.dto.*;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;
public interface ActorMediaAssetService extends ActorMediaAssetOwnershipVerifier {
    PageResult<ActorAssetRespDTO> list(Long userId, ActorAssetQueryDTO query);
    ActorAssetRespDTO asset(Long userId, Long assetId);
    ActorAssetRespDTO upload(Long userId, String mediaType, String categoryCode, MultipartFile file);
    ActorAssetRespDTO retryPdf(Long userId, Long failedAssetId, MultipartFile file);
    ActorAssetRespDTO createReadyAsset(Long userId, String mediaType, String categoryCode, PrivateActorMediaStorage.StoredObjectRef object, String name, String mimeType, Long sizeBytes);
    ActorAssetRespDTO update(Long userId, Long assetId, ActorAssetUpdateDTO request);
    void setCurrentResume(Long userId, ActorCurrentResumeUpdateDTO request);
    void bindProfileAsset(Long userId, Long assetId, String usageCode, Integer sortNo);
    List<ActorWorkAssetRespDTO> workAssets(Long userId, Long experienceId);
    void replaceWorkAssets(Long userId, Long experienceId, ActorWorkAssetsReplaceDTO request);
    ActorAssetAccessUrlRespDTO issueOwnerAccessUrl(Long userId, Long assetId);
    void delete(Long userId, Long assetId);
}
