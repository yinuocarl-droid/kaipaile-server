package com.kaipai.module.controller.admin.ai;

import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.R;
import com.kaipai.module.model.ai.dto.AdminAiImageProviderActionDTO;
import com.kaipai.module.model.ai.dto.AdminAiImageProviderDTO;
import com.kaipai.module.model.ai.dto.AdminAiImageProviderPublicConfigSaveDTO;
import com.kaipai.module.model.ai.dto.AdminAiImageProviderRevealSecretRespDTO;
import com.kaipai.module.model.ai.dto.AdminAiImageProviderSaveDTO;
import com.kaipai.module.model.ai.dto.AdminAiImageProviderSecretSaveDTO;
import com.kaipai.module.model.ai.dto.AdminAiImageProviderTestReqDTO;
import com.kaipai.module.model.ai.dto.AdminAiImageProviderTestRespDTO;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationRequest;
import com.kaipai.module.server.ai.profilecard.AiProfileImageGenerationResult;
import com.kaipai.module.server.ai.profilecard.AiGeneratedImageStorage;
import com.kaipai.module.server.ai.provider.AiProfileImageProvider;
import com.kaipai.module.server.ai.provider.AiProfileImageProviderRegistry;
import com.kaipai.module.server.ai.service.AiImageProviderConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "后台 AI 生图 provider 配置")
@RestController
@RequestMapping("/admin/ai/image-providers")
@RequiredArgsConstructor
public class AdminAiImageProviderController {

    private final AiImageProviderConfigService aiImageProviderConfigService;
    private final AiProfileImageProviderRegistry providerRegistry;
    private final AiGeneratedImageStorage generatedImageStorage;

    @Operation(summary = "AI 生图 provider 列表")
    @GetMapping
    @PreAuthorize("hasAuthority('page.system.ai-image-providers')")
    public R<List<AdminAiImageProviderDTO>> list() {
        return R.ok(aiImageProviderConfigService.adminList());
    }

    @Operation(summary = "AI 生图 provider 详情")
    @GetMapping("/{providerCode}")
    @PreAuthorize("hasAuthority('page.system.ai-image-providers')")
    public R<AdminAiImageProviderDTO> detail(@PathVariable String providerCode) {
        return R.ok(aiImageProviderConfigService.adminDetail(providerCode));
    }

    @Operation(summary = "新增或更新 AI 生图厂商接入信息")
    @PostMapping
    @PreAuthorize("hasAuthority('action.system.ai-image-provider.update')")
    public R<AdminAiImageProviderDTO> saveProvider(@Valid @RequestBody AdminAiImageProviderSaveDTO request) {
        return R.ok(aiImageProviderConfigService.saveProvider(request));
    }

    @Operation(summary = "保存 AI 生图 provider 公开配置")
    @PutMapping("/{providerCode}/public-config")
    @PreAuthorize("hasAuthority('action.system.ai-image-provider.update')")
    public R<AdminAiImageProviderDTO> savePublicConfig(@PathVariable String providerCode,
                                                       @Valid @RequestBody AdminAiImageProviderPublicConfigSaveDTO request) {
        return R.ok(aiImageProviderConfigService.savePublicConfig(providerCode, request));
    }

    @Operation(summary = "保存 AI 生图 provider 密钥")
    @PutMapping("/{providerCode}/secret")
    @PreAuthorize("hasAuthority('action.system.ai-image-provider.secret.update')")
    public R<AdminAiImageProviderDTO> saveSecret(@PathVariable String providerCode,
                                                 @RequestBody AdminAiImageProviderSecretSaveDTO request) {
        return R.ok(aiImageProviderConfigService.saveSecret(providerCode, request));
    }

    @Operation(summary = "清空 AI 生图 provider 密钥")
    @PostMapping("/{providerCode}/clear-secret")
    @PreAuthorize("hasAuthority('action.system.ai-image-provider.secret.update')")
    public R<AdminAiImageProviderDTO> clearSecret(@PathVariable String providerCode,
                                                  @RequestBody(required = false) AdminAiImageProviderActionDTO request) {
        return R.ok(aiImageProviderConfigService.clearSecret(providerCode, safeAction(request)));
    }

    @Operation(summary = "启用 AI 生图 provider")
    @PostMapping("/{providerCode}/enable")
    @PreAuthorize("hasAuthority('action.system.ai-image-provider.update')")
    public R<AdminAiImageProviderDTO> enable(@PathVariable String providerCode,
                                             @RequestBody(required = false) AdminAiImageProviderActionDTO request) {
        return R.ok(aiImageProviderConfigService.enable(providerCode, safeAction(request)));
    }

    @Operation(summary = "停用 AI 生图 provider")
    @PostMapping("/{providerCode}/disable")
    @PreAuthorize("hasAuthority('action.system.ai-image-provider.update')")
    public R<AdminAiImageProviderDTO> disable(@PathVariable String providerCode,
                                              @RequestBody(required = false) AdminAiImageProviderActionDTO request) {
        return R.ok(aiImageProviderConfigService.disable(providerCode, safeAction(request)));
    }

    @Operation(summary = "设为当前 AI 生图主 provider")
    @PostMapping("/{providerCode}/activate")
    @PreAuthorize("hasAuthority('action.system.ai-image-provider.activate')")
    public R<AdminAiImageProviderDTO> activate(@PathVariable String providerCode,
                                               @RequestBody(required = false) AdminAiImageProviderActionDTO request) {
        return R.ok(aiImageProviderConfigService.activate(providerCode, safeAction(request)));
    }

    @Operation(summary = "受控回显 AI 生图 provider 密钥")
    @PostMapping("/{providerCode}/reveal-secret")
    @PreAuthorize("hasAuthority('action.system.ai-image-provider.secret.view')")
    public R<AdminAiImageProviderRevealSecretRespDTO> revealSecret(@PathVariable String providerCode,
                                                                   @RequestBody(required = false) AdminAiImageProviderActionDTO request) {
        return R.ok(aiImageProviderConfigService.revealSecret(providerCode, safeAction(request)));
    }

    @Operation(summary = "测试 AI 生图 provider")
    @PostMapping("/{providerCode}/test")
    @PreAuthorize("hasAuthority('action.system.ai-image-provider.test')")
    public R<AdminAiImageProviderTestRespDTO> test(@PathVariable String providerCode,
                                                   @RequestBody(required = false) AdminAiImageProviderTestReqDTO request) {
        AdminAiImageProviderTestRespDTO result = new AdminAiImageProviderTestRespDTO();
        long started = System.currentTimeMillis();
        try {
            AiProfileImageProvider provider = providerRegistry.resolve(providerCode);
            String prompt = StringUtils.hasText(request == null ? null : request.getPrompt())
                    ? request.getPrompt().trim()
                    : "生成一张 9:16 演员资料卡背景图，保留参考图人物身份，不要出现文字、二维码和水印。";
            String templateSceneCode = StringUtils.hasText(request == null ? null : request.getTemplateSceneCode())
                    ? request.getTemplateSceneCode().trim()
                    : "classic";
            String styleCode = StringUtils.hasText(request == null ? null : request.getStyleCode())
                    ? request.getStyleCode().trim()
                    : templateSceneCode;
            String sourceImageUrl = request == null ? null : request.getSourceImageUrl();
            if (!StringUtils.hasText(sourceImageUrl)) {
                throw new BizException("测试生成需要填写 sourceImageUrl");
            }
            AiProfileImageGenerationResult imageResult = provider.generate(new AiProfileImageGenerationRequest(
                    "aitest_" + UUID.randomUUID().toString().replace("-", ""),
                    provider.modelCode(),
                    templateSceneCode,
                    styleCode,
                    sourceImageUrl.trim(),
                    prompt,
                    request == null ? null : request.getNegativePrompt(),
                    "{}"
            ));
            result.setProviderCode(provider.providerCode());
            result.setModelCode(provider.modelCode());
            result.setStatus("success");
            result.setMessage("测试生成成功");
            result.setImageUrl(persistTestImage(imageResult));
            result.setElapsedMs(System.currentTimeMillis() - started);
            aiImageProviderConfigService.recordTestResult(providerCode, "success", "测试生成成功");
            return R.ok(result);
        } catch (Exception error) {
            result.setProviderCode(providerCode);
            result.setStatus("failed");
            result.setMessage(error.getMessage());
            result.setElapsedMs(System.currentTimeMillis() - started);
            aiImageProviderConfigService.recordTestResult(providerCode, "failed", error.getMessage());
            return R.ok(result);
        }
    }

    private AdminAiImageProviderActionDTO safeAction(AdminAiImageProviderActionDTO request) {
        return request == null ? new AdminAiImageProviderActionDTO() : request;
    }

    private String persistTestImage(AiProfileImageGenerationResult imageResult) {
        if (imageResult == null) {
            throw new BizException("测试生成结果为空");
        }
        if (StringUtils.hasText(imageResult.imageUrl())) {
            return generatedImageStorage.uploadFromUrl(imageResult.imageUrl().trim(), "ai-profile-card-test");
        }
        if (imageResult.imageBytes() != null && imageResult.imageBytes().length > 0) {
            return generatedImageStorage.upload(
                    imageResult.imageBytes(),
                    StringUtils.hasText(imageResult.contentType()) ? imageResult.contentType() : "image/png",
                    "ai-profile-card-test");
        }
        throw new BizException("测试生成结果缺少图片内容");
    }
}
