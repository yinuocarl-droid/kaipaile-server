package com.kaipai.common.service;

import com.kaipai.common.dto.PdfUploadRespDTO;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.ResultCode;
import com.kaipai.integration.storage.CosUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfUploadService {

    private static final int MAX_PAGE_COUNT = 20;
    private static final int MAX_RENDER_WIDTH = 1200;
    private static final float RENDER_DPI = 72f;
    private static final float JPEG_QUALITY = 0.86f;
    private static final String PDF_FOLDER = "actor-resume-pdf";
    private static final String PDF_PAGE_FOLDER = "actor-resume-pdf-pages";

    static {
        ImageIO.scanForPlugins();
    }

    private final CosUtil cosUtil;

    public PdfUploadRespDTO uploadResumePdf(MultipartFile file) {
        String pdfUrl = null;
        List<String> pageUrls = new ArrayList<>();
        try {
            pdfUrl = cosUtil.uploadPdf(file, PDF_FOLDER);
            List<byte[]> pageImages = renderPageImages(file);
            for (byte[] pageImage : pageImages) {
                pageUrls.add(cosUtil.uploadBytes(pageImage, "image/jpeg", PDF_PAGE_FOLDER, ".jpg"));
            }

            PdfUploadRespDTO dto = new PdfUploadRespDTO();
            dto.setUrl(pdfUrl);
            dto.setName(normalizeFileName(file.getOriginalFilename()));
            dto.setPageCount(pageImages.size());
            dto.setPageImageUrls(pageUrls);
            return dto;
        } catch (BizException error) {
            cleanup(pdfUrl, pageUrls);
            throw error;
        } catch (Exception error) {
            cleanup(pdfUrl, pageUrls);
            log.warn("PDF 简历处理失败", error);
            throw new BizException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "PDF 简历处理失败，请确认文件未加密且页数不超过20页");
        }
    }

    private List<byte[]> renderPageImages(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getBytes())) {
            if (document.isEncrypted()) {
                throw new BizException("PDF 简历暂不支持加密文件");
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount <= 0) {
                throw new BizException("PDF 简历页数为空");
            }
            if (pageCount > MAX_PAGE_COUNT) {
                throw new BizException("PDF 简历页数不能超过20页");
            }

            PDFRenderer renderer = new PDFRenderer(document);
            List<byte[]> images = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                BufferedImage rendered = renderer.renderImageWithDPI(pageIndex, RENDER_DPI, ImageType.RGB);
                BufferedImage scaled = scaleToMaxWidth(rendered);
                images.add(writeJpeg(scaled));
            }
            return images;
        }
    }

    private BufferedImage scaleToMaxWidth(BufferedImage source) {
        if (source.getWidth() <= MAX_RENDER_WIDTH) {
            return source;
        }
        int targetHeight = Math.max(1, Math.round(source.getHeight() * (MAX_RENDER_WIDTH / (float) source.getWidth())));
        BufferedImage target = new BufferedImage(MAX_RENDER_WIDTH, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, MAX_RENDER_WIDTH, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] writeJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new BizException("当前运行环境缺少 JPG 编码器");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), param);
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private void cleanup(String pdfUrl, List<String> pageUrls) {
        if (StringUtils.hasText(pdfUrl)) {
            cosUtil.delete(pdfUrl);
        }
        for (String pageUrl : pageUrls) {
            if (StringUtils.hasText(pageUrl)) {
                cosUtil.delete(pageUrl);
            }
        }
    }

    private String normalizeFileName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "演员简历.pdf";
        }
        String normalized = originalFilename.replace("\\", "/");
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
    }
}
