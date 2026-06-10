package com.kaipai.controller.api.file;

import com.kaipai.common.dto.PdfUploadRespDTO;
import com.kaipai.common.result.R;
import com.kaipai.common.service.PdfUploadService;
import com.kaipai.integration.storage.CosUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "文件上传", description = "上传图片/视频到腾讯云 COS")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final CosUtil cosUtil;
    private final PdfUploadService pdfUploadService;

    @Operation(summary = "上传头像", description = "支持 jpg/png/webp，建议不超过 2MB")
    @PostMapping("/upload/avatar")
    public R<String> uploadAvatar(
            @Parameter(description = "头像文件") @RequestParam("file") MultipartFile file) {
        return R.ok(cosUtil.uploadImage(file, "avatar"));
    }

    @Operation(summary = "上传形象照/写真", description = "支持 jpg/png/webp，每张建议不超过 10MB")
    @PostMapping("/upload/photo")
    public R<String> uploadPhoto(
            @Parameter(description = "照片文件") @RequestParam("file") MultipartFile file) {
        return R.ok(cosUtil.uploadImage(file, "photo"));
    }

    @Operation(summary = "上传视频简历", description = "支持 mp4/mov/avi，建议不超过 100MB")
    @PostMapping("/upload/video")
    public R<String> uploadVideo(
            @Parameter(description = "视频文件") @RequestParam("file") MultipartFile file) {
        return R.ok(cosUtil.uploadVideo(file, "video"));
    }

    @Operation(summary = "上传 PDF 简历", description = "支持 pdf，建议不超过 20MB，页数不超过 20 页")
    @PostMapping("/upload/pdf")
    public R<PdfUploadRespDTO> uploadPdf(
            @Parameter(description = "PDF 简历文件") @RequestParam("file") MultipartFile file) {
        return R.ok(pdfUploadService.uploadResumePdf(file));
    }

    @Operation(summary = "上传营业执照", description = "支持 jpg/png，建议不超过 5MB")
    @PostMapping("/upload/license")
    public R<String> uploadLicense(
            @Parameter(description = "营业执照图片") @RequestParam("file") MultipartFile file) {
        return R.ok(cosUtil.uploadImage(file, "license"));
    }

    @Operation(summary = "删除文件", description = "传入文件的完整 URL 进行删除")
    @DeleteMapping("/delete")
    public R<Void> delete(
            @Parameter(description = "文件完整 URL") @RequestParam("url") String url) {
        cosUtil.delete(url);
        return R.ok();
    }
}
