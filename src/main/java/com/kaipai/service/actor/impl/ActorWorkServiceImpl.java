package com.kaipai.service.actor.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.mapper.actor.*;
import com.kaipai.mapper.card.ShareCardWorkMapper;
import com.kaipai.model.actor.dto.*;
import com.kaipai.model.actor.entity.*;
import com.kaipai.model.card.entity.ShareCardWork;
import com.kaipai.service.actor.ActorWorkService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ActorWorkServiceImpl implements ActorWorkService {
    private final ActorExperienceMapper experienceMapper;
    private final ActorProfileMapper profileMapper;
    private final ActorProfileRepresentativeWorkMapper representativeMapper;
    private final ShareCardWorkMapper shareCardWorkMapper;
    private final ObjectMapper objectMapper;

    public PageResult<ActorWorkRespDTO> listWorks(Long userId, ActorWorkQueryDTO query) {
        long pageNo = Math.max(1, query.getPage());
        long size = query.getSize() <= 0 ? 10 : Math.min(query.getSize(), 50);
        LambdaQueryWrapper<ActorExperience> wrapper = new LambdaQueryWrapper<ActorExperience>().eq(ActorExperience::getUserId, userId);
        if (StringUtils.hasText(query.getKeyword())) wrapper.and(q -> q.like(ActorExperience::getDramaName, query.getKeyword().trim()).or().like(ActorExperience::getRoleName, query.getKeyword().trim()));
        if (StringUtils.hasText(query.getPublishStatus())) wrapper.eq(ActorExperience::getPublishStatus, query.getPublishStatus());
        if (StringUtils.hasText(query.getWorkTypeCode())) wrapper.eq(ActorExperience::getWorkTypeCode, query.getWorkTypeCode());
        wrapper.orderByDesc(ActorExperience::getSortNo).orderByDesc(ActorExperience::getExperienceId);
        Page<ActorExperience> result = experienceMapper.selectPage(new Page<>(pageNo, size), wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords().stream().map(this::toResponse).toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public ActorWorkRespDTO createWork(Long userId, ActorWorkSaveDTO request) {
        ActorProfile profile = requireProfile(userId);
        String project = normalizeName(request.getProjectName()), role = normalizeName(request.getRoleName()), key = hash(project + "|" + role);
        if (experienceMapper.selectCount(new LambdaQueryWrapper<ActorExperience>().eq(ActorExperience::getUserId, userId).eq(ActorExperience::getDedupeKey, key)) > 0) throw ProfileDomainErrorCode.PROFILE_WORK_DUPLICATE.toException();
        ActorExperience work = new ActorExperience(); work.setUserId(userId); work.setActorProfileId(profile.getActorProfileId()); work.setSourceType("manual");
        apply(work, request, project, role, key); experienceMapper.insert(work); incrementVersion(profile); return toResponse(work);
    }

    public ActorWorkRespDTO work(Long userId, Long id) { return toResponse(requireWork(userId, id)); }

    @Transactional(rollbackFor = Exception.class)
    public ActorWorkRespDTO updateWork(Long userId, Long id, ActorWorkSaveDTO request) {
        ActorProfile profile = requireProfile(userId); ActorExperience work = requireWork(userId, id);
        String project = normalizeName(request.getProjectName()), role = normalizeName(request.getRoleName()), key = hash(project + "|" + role);
        if (experienceMapper.selectCount(new LambdaQueryWrapper<ActorExperience>().eq(ActorExperience::getUserId, userId).eq(ActorExperience::getDedupeKey, key).ne(ActorExperience::getExperienceId, id)) > 0) throw ProfileDomainErrorCode.PROFILE_WORK_DUPLICATE.toException();
        apply(work, request, project, role, key); experienceMapper.updateById(work); incrementVersion(profile); return toResponse(work);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteWork(Long userId, Long id) {
        ActorProfile profile = requireProfile(userId); requireWork(userId, id);
        if (representativeMapper.selectCount(new LambdaQueryWrapper<ActorProfileRepresentativeWork>().eq(ActorProfileRepresentativeWork::getExperienceId, id)) > 0
                || shareCardWorkMapper.selectCount(new LambdaQueryWrapper<ShareCardWork>().eq(ShareCardWork::getExperienceId, id)) > 0) {
            throw ProfileDomainErrorCode.PROFILE_WORK_IN_USE.toException();
        }
        experienceMapper.deleteById(id); incrementVersion(profile);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<ActorWorkRespDTO> replaceRepresentativeWorks(Long userId, ActorRepresentativeWorksUpdateDTO request) {
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(request.getExperienceIds()));
        if (ids.size() != request.getExperienceIds().size() || ids.size() > 6) throw new BizException("代表作最多 6 条且不能重复");
        ActorProfile profile = requireProfile(userId);
        List<ActorExperience> works = ids.isEmpty() ? List.of() : experienceMapper.selectList(new LambdaQueryWrapper<ActorExperience>().eq(ActorExperience::getUserId, userId).in(ActorExperience::getExperienceId, ids));
        if (works.size() != ids.size()) throw new BizException("代表作包含不属于当前用户的作品");
        representativeMapper.delete(new LambdaQueryWrapper<ActorProfileRepresentativeWork>().eq(ActorProfileRepresentativeWork::getActorProfileId, profile.getActorProfileId()));
        for (int i = 0; i < ids.size(); i++) { ActorProfileRepresentativeWork relation = new ActorProfileRepresentativeWork(); relation.setActorProfileId(profile.getActorProfileId()); relation.setExperienceId(ids.get(i)); relation.setSortNo(i + 1); representativeMapper.insert(relation); }
        incrementVersion(profile);
        return ids.stream().map(id -> works.stream().filter(w -> id.equals(w.getExperienceId())).findFirst().orElseThrow()).map(this::toResponse).toList();
    }

    private ActorProfile requireProfile(Long userId) { ActorProfile p = profileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>().eq(ActorProfile::getUserId, userId).last("limit 1")); if (p == null) throw new BizException("演员档案不存在"); return p; }
    private ActorExperience requireWork(Long userId, Long id) { ActorExperience w = experienceMapper.selectOne(new LambdaQueryWrapper<ActorExperience>().eq(ActorExperience::getUserId, userId).eq(ActorExperience::getExperienceId, id).last("limit 1")); if (w == null) throw new BizException("作品不存在"); return w; }
    private void incrementVersion(ActorProfile p) {
        if (profileMapper.incrementWorkLibraryVersion(p.getActorProfileId()) != 1) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_CONTEXT_VERSION_CONFLICT.toException();
        }
    }
    private void apply(ActorExperience w, ActorWorkSaveDTO r, String p, String role, String key) { w.setDramaName(r.getProjectName().trim()); w.setNormalizedDramaName(p); w.setRoleName(trim(r.getRoleName())); w.setNormalizedRoleName(role); w.setDedupeKey(key); w.setPublishStatus(trim(r.getPublishStatus())); w.setWorkTypeCode(trim(r.getWorkTypeCode())); w.setRoleLevelCode(trim(r.getRoleLevelCode())); w.setShootYear(r.getShootYear()); w.setShootMonth(r.getShootMonth()); w.setPlatform(trim(r.getPlatform())); w.setSyncSoundStatus(trim(r.getSyncSoundStatus())); w.setCollaboratorsJson(write(r.getCollaborators())); w.setAchievementText(trim(r.getAchievementText())); w.setRoleDesc(trim(r.getDescription())); }
    private ActorWorkRespDTO toResponse(ActorExperience w) { ActorWorkRespDTO d = new ActorWorkRespDTO(); d.setExperienceId(w.getExperienceId()); d.setProjectName(w.getDramaName()); d.setPublishStatus(w.getPublishStatus()); d.setWorkTypeCode(w.getWorkTypeCode()); d.setRoleLevelCode(w.getRoleLevelCode()); d.setRoleName(w.getRoleName()); d.setShootYear(w.getShootYear()); d.setShootMonth(w.getShootMonth()); d.setPlatform(w.getPlatform()); d.setSyncSoundStatus(w.getSyncSoundStatus()); d.setCollaborators(read(w.getCollaboratorsJson())); d.setAchievementText(w.getAchievementText()); d.setDescription(w.getRoleDesc()); return d; }
    private String normalizeName(String value) {
        if (!StringUtils.hasText(value)) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        normalized.codePoints().filter(Character::isLetterOrDigit).forEach(result::appendCodePoint);
        return result.toString();
    }
    private String hash(String v) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String trim(String v) { return StringUtils.hasText(v) ? v.trim() : null; }
    private String write(List<String> v) { try { return objectMapper.writeValueAsString(v == null ? List.of() : v); } catch (Exception e) { throw new IllegalStateException(e); } }
    private List<String> read(String v) { if (!StringUtils.hasText(v)) return new ArrayList<>(); try { return objectMapper.readValue(v, new TypeReference<List<String>>() {}); } catch (Exception e) { throw new IllegalStateException("work collaborators deserialization failed", e); } }
}
