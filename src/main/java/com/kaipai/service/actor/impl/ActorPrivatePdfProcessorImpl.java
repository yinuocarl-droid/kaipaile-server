package com.kaipai.service.actor.impl;

import com.kaipai.service.actor.ActorPrivatePdfProcessor;
import com.kaipai.service.actor.PrivateActorMediaStorage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class ActorPrivatePdfProcessorImpl implements ActorPrivatePdfProcessor {
    private static final int MAX_PAGES = 20;
    private static final int MAX_WIDTH = 1200;
    private final PrivateActorMediaStorage storage;

    @Override
    public List<PrivateActorMediaStorage.StoredObjectRef> process(Long userId, MultipartFile file) {
        List<PrivateActorMediaStorage.StoredObjectRef> pages = new ArrayList<>();
        try (PDDocument document = PDDocument.load(file.getBytes())) {
            if (document.isEncrypted()) throw failure("PDF_ENCRYPTED", "PDF 不支持加密文件");
            int count = document.getNumberOfPages();
            if (count <= 0 || count > MAX_PAGES) throw failure("PDF_PAGE_COUNT_INVALID", "PDF 页数必须为 1 到 20 页");
            PDFRenderer renderer = new PDFRenderer(document);
            for (int index = 0; index < count; index++) {
                BufferedImage rendered = renderer.renderImageWithDPI(index, 72f, ImageType.RGB);
                pages.add(storage.storeGenerated(userId, "pdf-page", jpeg(scale(rendered)), "image/jpeg", ".jpg"));
            }
            return pages;
        } catch (PdfProcessingException error) {
            pages.forEach(this::deleteBestEffort);
            throw error;
        } catch (Exception error) {
            pages.forEach(this::deleteBestEffort);
            throw failure("PDF_RENDER_FAILED", "PDF 页转换失败");
        }
    }

    private void deleteBestEffort(PrivateActorMediaStorage.StoredObjectRef page) {
        if (page == null || page.bucketCode() == null || page.objectKey() == null) return;
        try {
            storage.delete(page.bucketCode(), page.objectKey());
        } catch (RuntimeException ignored) {
            // Conversion failure remains primary; orphan cleanup can be retried operationally.
        }
    }

    private BufferedImage scale(BufferedImage source) {
        if (source.getWidth() <= MAX_WIDTH) return source;
        int height = Math.max(1, Math.round(source.getHeight() * (MAX_WIDTH / (float) source.getWidth())));
        BufferedImage target = new BufferedImage(MAX_WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, MAX_WIDTH, height, null);
        } finally { graphics.dispose(); }
        return target;
    }

    private byte[] jpeg(BufferedImage image) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw failure("JPEG_ENCODER_MISSING", "运行环境缺少 JPG 编码器");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) { param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); param.setCompressionQuality(0.86f); }
            writer.write(null, new IIOImage(image, null, null), param);
            return output.toByteArray();
        } finally { writer.dispose(); }
    }

    private PdfProcessingException failure(String code, String message) { return new PdfProcessingException(code, message); }
}
