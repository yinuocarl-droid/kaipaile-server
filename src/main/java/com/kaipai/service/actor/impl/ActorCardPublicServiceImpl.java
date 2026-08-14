package com.kaipai.service.actor.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardWorkMapper;
import com.kaipai.mapper.actor.ActorMediaAssetMapper;
import com.kaipai.model.actor.card.dto.ActorCardPublicRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardRespDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.actor.card.entity.ActorCardWork;
import com.kaipai.model.actor.entity.ActorMediaAsset;
import com.kaipai.service.actor.ActorCardPublicService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 已发布演员卡公开视图与复制创建（00-215 / 00-218）。
 * 数据现实：v2 向导的照片/视频/剧照存的是本地临时路径（wxfile://），非可公网访问 URL，
 * 公开视图对非 http(s) 资源返回空（观看者无法访问），资源上传链路接通后自然填充。
 */
@Service
@RequiredArgsConstructor
public class ActorCardPublicServiceImpl implements ActorCardPublicService {

    private final ActorCardMapper actorCardMapper;
    private final ActorCardWorkMapper actorCardWorkMapper;
    private final ActorMediaAssetMapper assetMapper;
    private final ObjectMapper objectMapper;

    @Override
    public ActorCardPublicRespDTO getPublicView(Long cardId) {
        ActorCard card = actorCardMapper.selectById(cardId);
        if (card == null) {
            throw new BizException(404, "演员卡不存在");
        }
        if (!"published".equals(card.getStatus())) {
            throw new BizException(403, "该演员卡尚未发布");
        }

        ActorCardPublicRespDTO resp = new ActorCardPublicRespDTO();
        resp.setId(card.getId());
        resp.setTitle(card.getTitle());
        resp.setStyle(card.getStyle());
        resp.setPreviewImageUrl(firstNonBlank(
                card.getExpandedImageUrl(), card.getSourceImageUrl(), card.getGeneratedPreviewUrl()));

        ActorCardPublicRespDTO.SettingsVO settings = parseSettings(card.getSettingsJson());
        resp.setSettings(settings);
        resp.setProfile(buildProfile(card.getProfileSnapshotJson(), Boolean.TRUE.equals(settings.getShowContact())));
        resp.setWorks(buildWorks(card.getId()));
        resp.setPhotos(parseUrlList(card.getPhotosJson()));
        resp.setVideo(buildVideo(card.getVideoUrl(), Boolean.TRUE.equals(settings.getShowVideo())));
        resp.setAttachment(buildAttachment(card.getAttachmentAssetId(), Boolean.TRUE.equals(settings.getShowAttachment())));
        return resp;
    }

    @Override
    public ActorCardRespDTO copy(Long userId, Long cardId) {
        ActorCard src = actorCardMapper.selectById(cardId);
        if (src == null || !userId.equals(src.getUserId())) {
            throw new BizException("演员卡不存在");
        }

        ActorCard copy = new ActorCard();
        copy.setUserId(userId);
        copy.setStatus("draft");
        copy.setTitle(src.getTitle());
        copy.setStyle(src.getStyle());
        copy.setCurrentStep(7);
        copy.setBackgroundImageUrl(src.getBackgroundImageUrl());
        copy.setSourceImageUrl(src.getSourceImageUrl());
        copy.setExpandedImageUrl(src.getExpandedImageUrl());
        copy.setProfileSnapshotJson(src.getProfileSnapshotJson());
        copy.setPhotosJson(src.getPhotosJson());
        copy.setVideoUrl(src.getVideoUrl());
        copy.setAttachmentAssetId(src.getAttachmentAssetId());
        copy.setSettingsJson(src.getSettingsJson());
        copy.setPublishedVersion(0);
        actorCardMapper.insert(copy);

        // 复制参演作品子表（生成门禁依赖 actor_card_work 行数）
        List<ActorCardWork> works = actorCardWorkMapper.selectList(new LambdaQueryWrapper<ActorCardWork>()
                .eq(ActorCardWork::getCardId, cardId)
                .orderByAsc(ActorCardWork::getSortOrder)
                .orderByAsc(ActorCardWork::getId));
        for (ActorCardWork w : works) {
            ActorCardWork nw = new ActorCardWork();
            nw.setCardId(copy.getId());
            nw.setSourceWorkId(w.getSourceWorkId());
            nw.setWorkTitle(w.getWorkTitle());
            nw.setWorkType(w.getWorkType());
            nw.setRoleName(w.getRoleName());
            nw.setStillsJson(w.getStillsJson());
            nw.setSortOrder(w.getSortOrder());
            actorCardWorkMapper.insert(nw);
        }

        ActorCardRespDTO dto = new ActorCardRespDTO();
        dto.setId(copy.getId());
        dto.setStatus("draft");
        dto.setTitle(copy.getTitle());
        dto.setStyle(copy.getStyle());
        dto.setCurrentStep(7);
        return dto;
    }

    // ── 组装 ───────────────────────────────────────────────────────────────────

    private ActorCardPublicRespDTO.ProfileVO buildProfile(String profileSnapshotJson, boolean showContact) {
        ActorCardPublicRespDTO.ProfileVO p = new ActorCardPublicRespDTO.ProfileVO();
        if (!StringUtils.hasText(profileSnapshotJson)) {
            return p;
        }
        try {
            JsonNode n = objectMapper.readTree(profileSnapshotJson);
            p.setName(text(n, "name"));
            p.setHeight(text(n, "height"));
            p.setCity(text(n, "city"));
            p.setSchool(text(n, "school"));
            p.setContact(showContact ? text(n, "contact") : null);
            p.setIntroduction(firstNonBlank(text(n, "introduction"), text(n, "intro")));
        } catch (Exception ignored) {
            // 快照非法时返回空 profile，不阻塞观看页
        }
        return p;
    }

    private List<ActorCardPublicRespDTO.WorkVO> buildWorks(Long cardId) {
        List<ActorCardPublicRespDTO.WorkVO> result = new ArrayList<>();
        List<ActorCardWork> works = actorCardWorkMapper.selectList(new LambdaQueryWrapper<ActorCardWork>()
                .eq(ActorCardWork::getCardId, cardId)
                .orderByAsc(ActorCardWork::getSortOrder)
                .orderByAsc(ActorCardWork::getId));
        for (ActorCardWork w : works) {
            ActorCardPublicRespDTO.WorkVO vo = new ActorCardPublicRespDTO.WorkVO();
            vo.setId(w.getId());
            vo.setTitle(w.getWorkTitle());
            vo.setRole(w.getRoleName());
            vo.setWorkType(w.getWorkType());
            vo.setStills(parseUrlList(w.getStillsJson()));
            result.add(vo);
        }
        return result;
    }

    private ActorCardPublicRespDTO.VideoVO buildVideo(String videoUrl, boolean showVideo) {
        if (!showVideo || !isHttpUrl(videoUrl)) {
            return null;
        }
        ActorCardPublicRespDTO.VideoVO v = new ActorCardPublicRespDTO.VideoVO();
        v.setUrl(videoUrl);
        return v;
    }

    private ActorCardPublicRespDTO.AttachmentVO buildAttachment(Long assetId, boolean showAttachment) {
        if (!showAttachment || assetId == null) {
            return null;
        }
        ActorMediaAsset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            return null;
        }
        ActorCardPublicRespDTO.AttachmentVO a = new ActorCardPublicRespDTO.AttachmentVO();
        a.setAssetId(assetId);
        a.setFilename(asset.getOriginalName());
        return a;
    }

    private ActorCardPublicRespDTO.SettingsVO parseSettings(String settingsJson) {
        ActorCardPublicRespDTO.SettingsVO s = new ActorCardPublicRespDTO.SettingsVO();
        if (!StringUtils.hasText(settingsJson)) {
            return s;
        }
        try {
            JsonNode n = objectMapper.readTree(settingsJson);
            if (n.has("showContact")) s.setShowContact(n.get("showContact").asBoolean());
            if (n.has("showVideo")) s.setShowVideo(n.get("showVideo").asBoolean());
            if (n.has("showAttachment")) s.setShowAttachment(n.get("showAttachment").asBoolean());
            if (n.hasNonNull("order") && n.get("order").isArray()) {
                List<String> order = new ArrayList<>();
                for (JsonNode item : n.get("order")) {
                    if (item.isTextual()) order.add(item.asText());
                }
                if (!order.isEmpty()) s.setOrder(order);
            }
        } catch (Exception ignored) {
            // 非法 settingsJson 时用默认值
        }
        return s;
    }

    /** 解析 JSON 字符串数组，仅保留可公网访问的 http(s) URL（本地 wxfile:// 临时路径不返回） */
    private List<String> parseUrlList(String json) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(json)) {
            return result;
        }
        try {
            JsonNode n = objectMapper.readTree(json);
            if (n.isArray()) {
                for (JsonNode item : n) {
                    if (item.isTextual() && isHttpUrl(item.asText())) {
                        result.add(item.asText());
                    }
                }
            }
        } catch (Exception ignored) {
            // 忽略非法 JSON
        }
        return result;
    }

    private boolean isHttpUrl(String value) {
        return StringUtils.hasText(value) && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isTextual() ? v.asText() : null;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) return v;
        }
        return null;
    }
}
