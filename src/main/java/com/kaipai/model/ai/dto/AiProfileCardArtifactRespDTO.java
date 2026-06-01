package com.kaipai.model.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiProfileCardArtifactRespDTO {

    private String artifactId;

    private String taskId;

    private String status;

    private String templateSceneCode;

    private String styleCode;

    private String providerCode;

    private String modelCode;

    private Long shareCardId;

    private String sourceImageUrl;

    private String generatedImageUrl;

    private AiProfileCardThemeRespDTO theme;

    /**
     * Legacy multi-page album payload kept for old clients.
     * The current artifact path exposes the generated cover through generatedImageUrl.
     */
    @Deprecated(since = "Phase 5", forRemoval = false)
    @Schema(description = "历史多页相册响应字段，仅用于兼容旧客户端；当前主路径使用 generatedImageUrl，不再写入新的 pages 数据。", deprecated = true)
    private List<AiProfileCardPageRespDTO> pages = new ArrayList<>();

    private LocalDateTime createTime;

    private LocalDateTime lastUpdate;
}
