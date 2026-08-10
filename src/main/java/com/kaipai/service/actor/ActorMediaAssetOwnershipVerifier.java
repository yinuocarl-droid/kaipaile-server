package com.kaipai.service.actor;

/**
 * 写入方在绑定素材前必须过这一关：素材归属本人、类型匹配、且已处理完成。
 * 不做默认实现——新增素材类型时让编译器逼每个实现点显式表态，
 * 避免默认放行或默认抛「素材不存在」掩盖真实原因。
 */
public interface ActorMediaAssetOwnershipVerifier {

    void requireOwnedReadyPhoto(Long userId, Long assetId);

    /** 附件简历（步骤 6）绑定前校验：本人 + mediaType=pdf + processStatus=ready。 */
    void requireOwnedReadyPdf(Long userId, Long assetId);
}
