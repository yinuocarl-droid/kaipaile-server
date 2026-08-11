package com.kaipai.service.actor.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.mapper.actor.ActorCardMapper;
import com.kaipai.mapper.actor.ActorCardWorkMapper;
import com.kaipai.mapper.actor.ActorMediaAssetMapper;
import com.kaipai.model.actor.card.dto.ActorCardRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardStepSaveReqDTO;
import com.kaipai.model.actor.card.dto.ActorCardWorkRespDTO;
import com.kaipai.model.actor.card.dto.ActorCardWorksReplaceReqDTO;
import com.kaipai.model.actor.card.entity.ActorCard;
import com.kaipai.model.actor.card.entity.ActorCardWork;
import com.kaipai.model.actor.entity.ActorMediaAsset;
import com.kaipai.service.actor.ActorCardDraftService;
import com.kaipai.service.actor.ActorMediaAssetOwnershipVerifier;
import com.kaipai.service.actor.support.ActorCardAttachmentCriterion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActorCardDraftServiceImpl implements ActorCardDraftService {

    /** 步骤3 每部作品最多 3 张剧照，与 ActorCardWorksReplaceReqDTO 的 @Size 约束保持一致 */
    private static final int MAX_STILLS_PER_WORK = 3;

    private final ActorCardMapper actorCardMapper;
    private final ActorCardWorkMapper actorCardWorkMapper;
    private final ActorMediaAssetMapper actorMediaAssetMapper;
    private final ActorMediaAssetOwnershipVerifier assetOwnershipVerifier;
    private final ObjectMapper objectMapper;

    @Override
    public ActorCardRespDTO createDraft(Long userId) {
        ActorCard card = new ActorCard();
        card.setUserId(userId);
        card.setStatus("draft");
        card.setCurrentStep(1);
        card.setPublishedVersion(0);
        actorCardMapper.insert(card);
        return toDto(card);
    }

    /**
     * 必须带事务：附件绑定要走 {@code requireOwnedReadyPdf}，而该校验是
     * {@code Propagation.MANDATORY} + {@code SELECT ... FOR UPDATE}，
     * 无事务时会直接抛 IllegalTransactionStateException。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveStep(Long userId, Long cardId, ActorCardStepSaveReqDTO dto) {
        ActorCard card = requireOwned(userId, cardId);
        if (dto.getCurrentStep() != null) card.setCurrentStep(dto.getCurrentStep());
        if (StringUtils.hasText(dto.getTitle())) card.setTitle(dto.getTitle());
        if (StringUtils.hasText(dto.getStyle())) card.setStyle(dto.getStyle());
        if (StringUtils.hasText(dto.getBackgroundImageUrl())) card.setBackgroundImageUrl(dto.getBackgroundImageUrl());
        if (StringUtils.hasText(dto.getSourceImageUrl())) card.setSourceImageUrl(dto.getSourceImageUrl());
        if (StringUtils.hasText(dto.getExpandedImageUrl())) card.setExpandedImageUrl(dto.getExpandedImageUrl());
        if (StringUtils.hasText(dto.getProfileSnapshotJson())) card.setProfileSnapshotJson(dto.getProfileSnapshotJson());
        if (dto.getPhotosJson() != null) card.setPhotosJson(dto.getPhotosJson());
        if (dto.getVideoUrl() != null) card.setVideoUrl(dto.getVideoUrl());
        applyAttachment(userId, card, dto.getAttachment());
        if (StringUtils.hasText(dto.getSettingsJson())) card.setSettingsJson(dto.getSettingsJson());
        actorCardMapper.updateById(card);
    }

    /**
     * 步骤6 附件三态：
     * <ul>
     *   <li>{@code binding == null}（不传键）→ 不动，跳过/下一步走这条</li>
     *   <li>{@code assetId != null} → 验权后绑定</li>
     *   <li>{@code assetId == null}（显式传 null）→ 清空</li>
     * </ul>
     * 清空时连历史 {@code attachmentUrl} 一起清：否则用户点了删除、素材 id 清了，
     * 历史 URL 还在，步骤 6 判据仍为真，页面会永远停在「已添加」且无法再次删除。
     * <p>{@code dto.attachmentUrl} 已停写，此处刻意不采纳其新值。
     */
    private void applyAttachment(Long userId, ActorCard card, ActorCardStepSaveReqDTO.AttachmentBinding binding) {
        if (binding == null) {
            return;
        }
        if (binding.getAssetId() == null) {
            card.setAttachmentAssetId(null);
            card.setAttachmentUrl(null);
            return;
        }
        assetOwnershipVerifier.requireOwnedReadyPdf(userId, binding.getAssetId());
        card.setAttachmentAssetId(binding.getAssetId());
        card.setAttachmentUrl(null);
    }

    @Override
    public ActorCardRespDTO getDraft(Long userId, Long cardId) {
        return toDto(requireOwned(userId, cardId));
    }

    @Override
    public List<ActorCardRespDTO> listDrafts(Long userId) {
        return actorCardMapper.selectList(new LambdaQueryWrapper<ActorCard>()
                        .eq(ActorCard::getUserId, userId)
                        .eq(ActorCard::getStatus, "draft")
                        .orderByDesc(ActorCard::getLastUpdate))
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public void deleteDraft(Long userId, Long cardId) {
        ActorCard card = requireOwned(userId, cardId);
        if (!"draft".equals(card.getStatus())) {
            throw new BizException("只允许删除草稿状态的演员卡");
        }
        actorCardMapper.deleteById(cardId);
    }

    // ── 步骤3：参演作品快照 ────────────────────────────────────────────────────

    /**
     * 整体替换语义：步骤3 的「下一步」提交的是当前完整勾选结果，
     * 增量 add/remove 会让前端在放弃勾选时还得额外发删除请求，容易漏删导致子表与页面不一致。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceWorks(Long userId, Long cardId, ActorCardWorksReplaceReqDTO dto) {
        requireOwned(userId, cardId);
        // BaseEntity 带 @TableLogic，这里是逻辑删除：旧行留库但被 selectCount/selectList 自动过滤，
        // 因此重复提交不会把作品数虚高，代价是反复编辑会累积历史行（可接受，与本项目其他子表一致）。
        actorCardWorkMapper.delete(new LambdaQueryWrapper<ActorCardWork>()
                .eq(ActorCardWork::getCardId, cardId));
        List<ActorCardWorksReplaceReqDTO.WorkItem> items = dto.getWorks();
        for (int i = 0; i < items.size(); i++) {
            ActorCardWorksReplaceReqDTO.WorkItem item = items.get(i);
            if (item.getStills().size() > MAX_STILLS_PER_WORK) {
                throw new BizException("《" + item.getWorkTitle() + "》剧照最多 " + MAX_STILLS_PER_WORK + " 张");
            }
            ActorCardWork work = new ActorCardWork();
            work.setCardId(cardId);
            work.setSourceWorkId(item.getSourceWorkId());
            work.setWorkTitle(item.getWorkTitle());
            work.setWorkType(item.getWorkType());
            work.setRoleName(item.getRoleName());
            work.setStillsJson(writeUrlList(item.getStills()));
            work.setSortOrder(i);
            actorCardWorkMapper.insert(work);
        }
    }

    @Override
    public List<ActorCardWorkRespDTO> listWorks(Long userId, Long cardId) {
        requireOwned(userId, cardId);
        return queryWorks(cardId).stream().map(w -> {
            ActorCardWorkRespDTO dto = new ActorCardWorkRespDTO();
            dto.setId(w.getId());
            dto.setSourceWorkId(w.getSourceWorkId());
            dto.setWorkTitle(w.getWorkTitle());
            dto.setWorkType(w.getWorkType());
            dto.setRoleName(w.getRoleName());
            dto.setStills(readUrlList(w.getStillsJson()));
            dto.setSortOrder(w.getSortOrder());
            return dto;
        }).collect(Collectors.toList());
    }

    private List<ActorCardWork> queryWorks(Long cardId) {
        return actorCardWorkMapper.selectList(new LambdaQueryWrapper<ActorCardWork>()
                .eq(ActorCardWork::getCardId, cardId)
                .orderByAsc(ActorCardWork::getSortOrder)
                .orderByAsc(ActorCardWork::getId));
    }

    private String writeUrlList(List<String> urls) {
        try {
            return objectMapper.writeValueAsString(urls == null ? Collections.emptyList() : urls);
        } catch (Exception e) {
            throw new BizException("剧照数据序列化失败");
        }
    }

    /**
     * 在边界反序列化，前端拿到结构化列表，不必自己 JSON.parse
     *（前端裸 parse 已是登记在案的缺陷来源）。剧照与生活照共用。
     */
    private List<String> readUrlList(String json) {
        if (!StringUtils.hasText(json)) return new ArrayList<>();
        try {
            List<String> parsed = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return parsed == null ? new ArrayList<>() : parsed;
        } catch (Exception e) {
            // 脏数据不应让整张卡读不出来，退化为空剧照，由步骤3 页面提示重新添加
            return new ArrayList<>();
        }
    }

    private ActorCard requireOwned(Long userId, Long cardId) {
        ActorCard card = actorCardMapper.selectById(cardId);
        if (card == null) throw new BizException("演员卡不存在");
        if (!userId.equals(card.getUserId())) throw new BizException("无权操作该演员卡");
        return card;
    }

    private ActorCardRespDTO toDto(ActorCard card) {
        ActorCardRespDTO dto = new ActorCardRespDTO();
        dto.setId(card.getId());
        dto.setStatus(card.getStatus());
        dto.setTitle(card.getTitle());
        dto.setStyle(card.getStyle());
        dto.setCurrentStep(card.getCurrentStep() != null ? card.getCurrentStep() : 1);
        dto.setBackgroundImageUrl(card.getBackgroundImageUrl());
        dto.setSourceImageUrl(card.getSourceImageUrl());
        dto.setExpandedImageUrl(card.getExpandedImageUrl());
        dto.setGeneratedPreviewUrl(card.getGeneratedPreviewUrl());
        dto.setProfileSnapshotJson(card.getProfileSnapshotJson());
        dto.setPhotosJson(card.getPhotosJson());
        dto.setVideoUrl(card.getVideoUrl());
        dto.setAttachmentUrl(card.getAttachmentUrl());
        dto.setAttachmentAssetId(card.getAttachmentAssetId());
        fillAttachmentDerived(dto, card);
        dto.setSettingsJson(card.getSettingsJson());
        dto.setPublishedVersion(card.getPublishedVersion());
        dto.setPublishedAt(card.getPublishedAt());
        dto.setCreateTime(card.getCreateTime());
        dto.setLastUpdate(card.getLastUpdate());
        // 步骤状态与完整度必须同源：此前两者各算一套，同一张卡在 Hub 页与列表页会给出互相矛盾的结论
        List<ActorCardRespDTO.StepStatus> statuses = deriveStepStatuses(card);
        dto.setStepStatuses(statuses);
        dto.setCompletionPercentage(calcCompletion(statuses));
        return dto;
    }

    /**
     * 附件派生字段在服务端 join 资产表填充，前端渲染文件卡不必再发一次请求。
     * 按 userId 一起过滤：卡本身已验归属，这里再收一次口，
     * 避免历史脏数据（卡上残留了他人 assetId）把别人的文件名回读出去。
     * 素材查不到（已删除）时三个派生字段留空，由页面提示重新添加，不让整张卡读不出来。
     */
    private void fillAttachmentDerived(ActorCardRespDTO dto, ActorCard card) {
        if (card.getAttachmentAssetId() == null) {
            return;
        }
        ActorMediaAsset asset = actorMediaAssetMapper.selectOne(new LambdaQueryWrapper<ActorMediaAsset>()
                .eq(ActorMediaAsset::getAssetId, card.getAttachmentAssetId())
                .eq(ActorMediaAsset::getUserId, card.getUserId())
                .last("limit 1"));
        if (asset == null) {
            return;
        }
        dto.setAttachmentName(asset.getOriginalName());
        dto.setAttachmentPageCount(asset.getPageCount());
        dto.setAttachmentStatus(asset.getProcessStatus());
    }

    // ── 步骤状态派生（唯一真源） ───────────────────────────────────────────────

    /**
     * 步骤状态只在这里算一次。
     * 步骤3 必须查子表：只有后端知道 actor_card_work 的行数，
     * 而该行数同时也是 ActorCardGenerateService 生成门禁的判定依据，
     * 前端无法自行推断，硬编码就会出现「Hub 显示未添加、生成却已满足」这类错位。
     */
    private List<ActorCardRespDTO.StepStatus> deriveStepStatuses(ActorCard card) {
        List<ActorCardRespDTO.StepStatus> list = new ArrayList<>();

        boolean hasMainImage = StringUtils.hasText(card.getExpandedImageUrl())
                || StringUtils.hasText(card.getSourceImageUrl());
        list.add(step(1, hasMainImage ? "done" : "empty", hasMainImage ? "已完成" : "待完成"));

        boolean hasProfile = StringUtils.hasText(card.getProfileSnapshotJson());
        list.add(step(2, hasProfile ? "done" : "pending", hasProfile ? "已完成" : "待确认"));

        long workCount = actorCardWorkMapper.selectCount(new LambdaQueryWrapper<ActorCardWork>()
                .eq(ActorCardWork::getCardId, card.getId()));
        list.add(step(3, workCount > 0 ? "done" : "empty",
                workCount > 0 ? workCount + "部" : "未添加"));

        int photoCount = readUrlList(card.getPhotosJson()).size();
        list.add(step(4, photoCount > 0 ? "done" : "empty", photoCount + "张"));

        boolean hasVideo = StringUtils.hasText(card.getVideoUrl());
        list.add(step(5, hasVideo ? "done" : "empty", hasVideo ? "已添加" : "未添加"));

        // 判据必须认 assetId：漏改会让步骤 6 在附件已绑定时仍显示「未添加」，
        // 功能全对但用户看不到。历史 attachmentUrl 一并认，供老草稿显示与删除。
        boolean hasAttachment = ActorCardAttachmentCriterion.hasAttachment(card);
        list.add(step(6, hasAttachment ? "done" : "empty", hasAttachment ? "已添加" : "未添加"));

        boolean hasSettings = StringUtils.hasText(card.getSettingsJson());
        list.add(step(7, hasSettings ? "done" : "empty", hasSettings ? "已完成" : "待完成"));

        return list;
    }

    private ActorCardRespDTO.StepStatus step(int step, String statusCode, String statusLabel) {
        ActorCardRespDTO.StepStatus s = new ActorCardRespDTO.StepStatus();
        s.setStep(step);
        s.setStatusCode(statusCode);
        s.setStatusLabel(statusLabel);
        return s;
    }

    private int calcCompletion(List<ActorCardRespDTO.StepStatus> statuses) {
        long done = statuses.stream().filter(s -> "done".equals(s.getStatusCode())).count();
        return (int) Math.round(done * 100.0 / statuses.size());
    }
}
