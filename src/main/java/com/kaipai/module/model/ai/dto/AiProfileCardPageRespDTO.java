package com.kaipai.module.model.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Legacy multi-page album page response kept for old clients.
 * The current profile-card path exposes a single generated cover via generatedImageUrl on task/artifact DTOs.
 */
@Deprecated(since = "Phase 5", forRemoval = false)
@Data
@Schema(description = "历史 AI 分享图多页相册页面响应，仅用于兼容旧客户端；当前主路径不再依赖该结构。", deprecated = true)
public class AiProfileCardPageRespDTO {

    private Integer pageNo;

    private String pageType;

    private String status;

    private String providerCode;

    private String modelCode;

    private String generatedImageUrl;

    private String promptLocale;

    @Deprecated(since = "Phase 5", forRemoval = false)
    @Schema(description = "历史多页连续性模式，仅用于旧相册链路兼容；当前主路径不读取。", deprecated = true)
    private String continuityMode;

    @Deprecated(since = "Phase 5", forRemoval = false)
    @Schema(description = "历史多页连续性参考图地址，仅用于旧相册链路兼容；当前主路径不读取。", deprecated = true)
    private String continuityReferenceUrl;

    @Deprecated(since = "Phase 5", forRemoval = false)
    @Schema(description = "历史多页连续性参考来源页类型，仅用于旧相册链路兼容；当前主路径不读取。", deprecated = true)
    private String continuityReferenceSourcePageType;

    @Deprecated(since = "Phase 5", forRemoval = false)
    @Schema(description = "历史多页连续性参考来源页序号，仅用于旧相册链路兼容；当前主路径不读取。", deprecated = true)
    private Integer continuityReferenceSourcePageNo;

    @Deprecated(since = "Phase 5", forRemoval = false)
    @Schema(description = "历史多页尾部参考带裁切比例，仅用于旧相册链路兼容；当前主路径不读取。", deprecated = true)
    private Double continuityBandRatio;

    @Deprecated(since = "Phase 5", forRemoval = false)
    @Schema(description = "历史多页尾部参考带裁切区域，仅用于旧相册链路兼容；当前主路径不读取。", deprecated = true)
    private String continuityBandRect;

    @Deprecated(since = "Phase 5", forRemoval = false)
    @Schema(description = "历史多页连续性降级或裁切失败原因，仅用于旧相册链路兼容；当前主路径不读取。", deprecated = true)
    private String continuityFailureReason;

    private String failureReason;

    private LocalDateTime createTime;

    private LocalDateTime lastUpdate;
}
