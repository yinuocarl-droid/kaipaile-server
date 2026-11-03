package com.kaipai.service.actor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardBackgroundMapper;
import com.kaipai.mapper.actor.ActorCardBackgroundAdminMapper;
import com.kaipai.model.actor.card.dto.AdminActorCardBackgroundDTO.*;
import com.kaipai.model.actor.card.entity.ActorCardBackground;
import com.kaipai.service.ai.profilecard.AiGeneratedImageStorage;
import java.io.*;
import java.time.Duration;
import java.util.*;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Owns the shared public catalog. Does not load private media or invoke AI. */
@Service
@RequiredArgsConstructor
public class AdminActorCardBackgroundService {
    private final ActorCardBackgroundMapper mapper;
    private final ActorCardBackgroundAdminMapper adminMapper;
    private final AiGeneratedImageStorage storage;
    private final AdminAuthContext auth;
    private final AdminOperationLogger audit;
    private static final Set<String> STYLES = Set.of("classic", "urban", "ancient", "fresh");
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    /** Paged governance view; consumer endpoint uses the same rows, enabled filter and order. */
    public List<Item> list(String style, boolean includeDisabled, int page, int size) {
        if (style != null && !style.isBlank()) requireStyle(style);
        if (page < 1 || page > 10000 || size < 1 || size > 100) throw new BizException("分页参数不合法");
        var q = new LambdaQueryWrapper<ActorCardBackground>()
                .eq(style != null && !style.isBlank(), ActorCardBackground::getStyle, style)
                .eq(!includeDisabled, ActorCardBackground::getEnabled, 1)
                .orderByAsc(ActorCardBackground::getSortOrder, ActorCardBackground::getId)
                .last("LIMIT " + size + " OFFSET " + ((page - 1) * size));
        return mapper.selectList(q).stream().map(this::item).toList();
    }

    /** Validate/decode/reencode before storage; creating a new asset never replaces old IDs. */
    @Transactional(rollbackFor = Exception.class)
    public Item upload(MultipartFile file, String style, int sortOrder) {
        requireAdmin(); requireStyle(style); requireMetadata(sortOrder, 1);
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) throw new BizException("请上传不超过10MB的JPEG或PNG图片");
        byte[] clean; String type;
        try (var in = ImageIO.createImageInputStream(file.getInputStream())) {
            if (in == null) throw new BizException("无法读取背景图片");
            var readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new BizException("文件不是可解码的JPEG或PNG图片");
            var reader = readers.next();
            try {
                reader.setInput(in);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg").contains(format)) throw new BizException("背景仅支持JPEG或PNG");
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || w > 10000 || h > 10000 || (long) w * h > 20000000) throw new BizException("背景图片尺寸过大");
                var image = reader.read(0);
                var out = new ByteArrayOutputStream();
                if (!ImageIO.write(image, format.equals("png") ? "png" : "jpeg", out)) throw new BizException("背景图片格式不支持");
                clean = out.toByteArray(); type = format.equals("png") ? "image/png" : "image/jpeg";
                if (clean.length > MAX_BYTES) throw new BizException("处理后的背景图片超过10MB");
            } finally { reader.dispose(); }
        } catch (BizException ex) { throw ex; }
        catch (IOException | RuntimeException ex) { throw new BizException("背景图片无法解码，请更换JPEG或PNG"); }
        String url = storage.upload(clean, type, "actor-card-background");
        var bg = new ActorCardBackground(); bg.setStyle(style); bg.setSortOrder(sortOrder); bg.setEnabled(1);
        bg.setImageUrl(url); bg.setThumbnailUrl(url);
        if (mapper.insert(bg) != 1) throw new BizException(409, "背景保存冲突");
        log(bg.getId(), "upload_public_background", Map.of("style", style, "sortOrder", sortOrder));
        return item(bg);
    }

    /** Metadata-only governance under a row lock and explicit comparison with the client's old values. */
    @Transactional(rollbackFor = Exception.class)
    public Item update(Long id, UpdateReq req) {
        requireAdmin(); requireStyle(req.style()); requireMetadata(req.sortOrder(), req.enabled());
        var old = requireBackground(adminMapper.lockBackground(id));
        if (!Objects.equals(old.getStyle(), req.expectedStyle()) || !Objects.equals(old.getSortOrder(), req.expectedSortOrder())
                || !Objects.equals(old.getEnabled(), req.expectedEnabled())) throw new BizException(409, "背景已更新，请刷新图库再操作");
        if (adminMapper.updateMetadata(id, req.style(), req.sortOrder(), req.enabled()) != 1) throw new BizException(409, "背景保存冲突");
        log(id, "update_public_background", Map.of("style", req.style(), "enabled", req.enabled(), "sortOrder", req.sortOrder(),
                "beforeStyle", old.getStyle(), "beforeEnabled", old.getEnabled(), "beforeSortOrder", old.getSortOrder()));
        old.setStyle(req.style()); old.setSortOrder(req.sortOrder()); old.setEnabled(req.enabled());
        return item(old);
    }

    /** Read without creating editor state or touching card assignments. */
    public ContextResp editorBackground(Long templateId) {
        requireTemplate(templateId);
        try {
            var state = adminMapper.context(templateId);
            var background = state == null || state.getBackgroundId() == null ? null : mapper.selectById(state.getBackgroundId());
            return new ContextResp(state == null ? 0 : state.getRevision(), state == null ? null : state.getBackgroundId(), background == null ? null : item(background));
        } catch (InvalidDataAccessResourceUsageException ex) { throw new BizException(503, "编辑背景存储未就绪，请执行B7迁移"); }
    }

    /** Save editor context independently from layout draft/publication, with revision protection. */
    @Transactional(rollbackFor = Exception.class)
    public ContextResp saveEditorBackground(Long templateId, ContextSaveReq req) {
        Long admin = requireAdmin(); requireTemplate(templateId);
        if (req.expectedRevision() == null || req.expectedRevision() < 0) throw new BizException("expectedRevision无效");
        if (req.backgroundId() != null) {
            var bg = requireBackground(adminMapper.lockBackground(req.backgroundId()));
            if (!Integer.valueOf(1).equals(bg.getEnabled())) throw new BizException(409, "该背景已停用，请重新选择");
        }
        try {
            adminMapper.initializeContext(templateId, admin);
            var before = adminMapper.lockContext(templateId);
            if (before == null || !Objects.equals(before.getRevision(), req.expectedRevision()) || before.getRevision() == Long.MAX_VALUE) throw new BizException(409, "编辑背景已变更，请重新读取");
            if (adminMapper.saveContext(templateId, req.expectedRevision(), req.backgroundId(), admin) != 1) throw new BizException(409, "编辑背景保存冲突");
            var context = new LinkedHashMap<String, Object>(); context.put("backgroundId", req.backgroundId()); context.put("revision", req.expectedRevision());
            log(templateId, "select_editor_background", context);
            return editorBackground(templateId);
        } catch (InvalidDataAccessResourceUsageException ex) { throw new BizException(503, "编辑背景存储未就绪，请执行B7迁移"); }
    }

    private Item item(ActorCardBackground bg) {
        String url = storage.issueReadUrl(bg.getImageUrl(), Duration.ofMinutes(10));
        String thumb = bg.getThumbnailUrl() == null || bg.getThumbnailUrl().isBlank() || bg.getThumbnailUrl().equals(bg.getImageUrl())
                ? url : storage.issueReadUrl(bg.getThumbnailUrl(), Duration.ofMinutes(10));
        return new Item(bg.getId(), bg.getStyle(), url, thumb, bg.getSortOrder(), bg.getEnabled());
    }
    private Long requireAdmin() { Long id = auth.getCurrentAdminUserId(); if (id == null || id <= 0) throw new BizException(401, "请使用后台管理员身份"); return id; }
    private void requireStyle(String style) { if (!STYLES.contains(style == null ? "" : style)) throw new BizException("背景风格仅支持classic/urban/ancient/fresh"); }
    private void requireMetadata(int order, int enabled) { if (order < 0 || order > 99999 || enabled < 0 || enabled > 1) throw new BizException("排序或启用状态无效"); }
    private void requireTemplate(Long id) { if (id == null || id <= 0 || adminMapper.templateExists(id) != 1) throw new BizException(404, "模板不存在"); }
    private ActorCardBackground requireBackground(ActorCardBackground bg) { if (bg == null) throw new BizException(404, "背景不存在"); return bg; }
    private void log(Long id, String op, Object context) { audit.logRequired(AdminOperationLogCommand.builder().moduleCode("content").operationCode(op).targetType("actor_card_public_background").targetId(id).extraContext(context).operationResult(1).build()); }
}
