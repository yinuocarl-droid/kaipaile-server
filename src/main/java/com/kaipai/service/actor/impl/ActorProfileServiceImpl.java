package com.kaipai.service.actor.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.actor.dto.ActorPhotoCategoriesDTO;
import com.kaipai.model.actor.dto.ActorProfileDTO;
import com.kaipai.model.actor.dto.ActorProfileSaveDTO;
import com.kaipai.model.actor.dto.ActorSearchQueryDTO;
import com.kaipai.model.actor.dto.ActorWorkExperienceDTO;
import com.kaipai.model.actor.entity.ActorExperience;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.model.user.entity.User;
import com.kaipai.mapper.actor.ActorExperienceMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.service.actor.ActorProfileService;
import com.kaipai.service.ai.AiResumeApplyRecorder;
import com.kaipai.service.capability.CapabilityAccountService;
import com.kaipai.service.referral.ReferralRecordService;
import com.kaipai.mapper.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ActorProfileServiceImpl extends ServiceImpl<ActorProfileMapper, ActorProfile> implements ActorProfileService {

    private final ActorExperienceMapper actorExperienceMapper;
    private final UserMapper userMapper;
    private final CapabilityAccountService capabilityAccountService;
    private final ObjectMapper objectMapper;
    private final AiResumeApplyRecorder aiResumeApplyRecorder;
    private final ReferralRecordService referralRecordService;

    @Override
    public ActorProfileDTO mine(Long currentUserId) {
        return buildProfile(currentUserId, true);
    }

    @Override
    public ActorProfileDTO profile(Long userId) {
        return buildProfile(userId, false);
    }

    @Override
    public ActorProfileDTO detail(Long userId, boolean includeContact) {
        return buildProfile(userId, includeContact);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveProfile(Long currentUserId, ActorProfileSaveDTO dto) {
        User user = requireUser(currentUserId);
        ActorProfile profile = getOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, currentUserId)
                .last("limit 1"), false);
        boolean creating = profile == null;
        if (creating && hasAiResumeApplyMeta(dto)) {
            throw new BizException("演员档案不存在，不能应用 AI 草稿");
        }
        ActorProfileDTO beforeProfile = creating ? null : buildProfile(currentUserId, true);
        if (profile == null) {
            profile = new ActorProfile();
            profile.setUserId(currentUserId);
            profile.setProfileStatus(1);
        }

        profile.setNickName(trimToNull(dto.getName()));
        profile.setGender(toGenderCode(dto.getGender()));
        profile.setAge(dto.getAge());
        profile.setHeight(dto.getHeight());
        profile.setWeight(dto.getWeight());
        profile.setLocationCity(trimToNull(dto.getCity()));
        profile.setBirthday(parseBirthday(dto.getBirthday()));
        profile.setBirthHour(trimToNull(dto.getBirthHour()));
        profile.setAvatarUrl(trimToNull(dto.getAvatar()));
        profile.setIntro(trimToNull(dto.getIntro()));
        profile.setPhotoUrls(writeJson(safeList(dto.getPhotos())));
        profile.setVideoUrl(trimToNull(dto.getVideoUrl()));
        profile.setSkillTag(writeCommaSeparated(dto.getSkillTypes()));
        profile.setStyleTag(writeStyleTags(dto));
        profile.setExperienceDesc(writeExperienceSummary(dto.getWorkExperiences()));
        profile.setPhone(firstNonBlank(dto.getContactPhone(), user.getPhone(), null));
        profile.setIsCertified(user.getRealAuthStatus() != null && user.getRealAuthStatus() == 2);
        profile.setExtendedField(writeJson(buildProfileExtras(dto, readProfileExtras(profile.getExtendedField()))));

        if (creating) {
            save(profile);
        } else {
            updateById(profile);
        }

        syncExperiences(profile, dto.getWorkExperiences());
        referralRecordService.reconcileInviteeReferral(currentUserId);
        if (hasAiResumeApplyMeta(dto)) {
            aiResumeApplyRecorder.recordAppliedDraft(currentUserId, beforeProfile, dto);
        }
    }

    @Override
    public PageResult<ActorProfileDTO> search(ActorSearchQueryDTO query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 10 : query.getSize();
        Page<ActorProfile> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<ActorProfile> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getGender())) {
            wrapper.eq(ActorProfile::getGender, toGenderCode(query.getGender()));
        }
        if (query.getMinAge() != null) {
            wrapper.ge(ActorProfile::getAge, query.getMinAge());
        }
        if (query.getMaxAge() != null) {
            wrapper.le(ActorProfile::getAge, query.getMaxAge());
        }
        if (StringUtils.hasText(query.getCity())) {
            wrapper.like(ActorProfile::getLocationCity, query.getCity().trim());
        }
        if (StringUtils.hasText(query.getSkillType())) {
            wrapper.like(ActorProfile::getSkillTag, query.getSkillType().trim());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(inner -> inner.like(ActorProfile::getNickName, keyword)
                    .or()
                    .like(ActorProfile::getRealName, keyword)
                    .or()
                    .like(ActorProfile::getIntro, keyword));
        }
        wrapper.orderByDesc(ActorProfile::getSortNo)
                .orderByDesc(ActorProfile::getLastUpdate)
                .orderByDesc(ActorProfile::getActorProfileId);

        Page<ActorProfile> result = page(page, wrapper);
        List<ActorProfileDTO> list = result.getRecords().stream()
                .map(item -> buildProfile(item.getUserId(), false))
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    private ActorProfileDTO buildProfile(Long userId, boolean includePrivate) {
        User user = requireUser(userId);
        ActorProfile profile = getOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, userId)
                .last("limit 1"), false);
        if (profile == null) {
            throw new BizException("演员档案不存在");
        }

        ProfileExtras extras = readProfileExtras(profile == null ? null : profile.getExtendedField());
        List<ActorWorkExperienceDTO> experiences = loadExperiences(userId);

        ActorProfileDTO dto = new ActorProfileDTO();
        dto.setUserId(userId);
        dto.setName(firstNonBlank(profile == null ? null : profile.getNickName(), user.getUserName(), ""));
        dto.setGender(toGenderText(profile == null ? null : profile.getGender()));
        dto.setAge(profile == null ? 0 : defaultInt(profile.getAge()));
        dto.setHeight(profile == null ? 0 : defaultInt(profile.getHeight()));
        dto.setWeight(profile == null ? 0 : defaultInt(profile.getWeight()));
        dto.setCity(profile == null ? "" : defaultText(profile.getLocationCity()));
        dto.setBirthday(profile != null && profile.getBirthday() != null ? profile.getBirthday().toString() : null);
        dto.setBirthHour(profile == null ? null : profile.getBirthHour());
        dto.setAvatar(firstNonBlank(profile == null ? null : profile.getAvatarUrl(), user.getAvatarUrl(), ""));
        dto.setIntro(profile == null ? "" : defaultText(profile.getIntro()));
        dto.setPhotos(profile == null ? new ArrayList<>() : readStringList(profile.getPhotoUrls()));
        dto.setPhotoCategories(resolvePhotoCategories(extras.photoCategories, dto.getPhotos()));
        dto.setVideoUrl(profile == null ? "" : defaultText(profile.getVideoUrl()));
        dto.setSkillTypes(profile == null ? new ArrayList<>() : splitCommaSeparated(profile.getSkillTag()));
        dto.setWorkExperiences(experiences);
        dto.setBodyType(extras.bodyType);
        dto.setHairStyle(extras.hairStyle);
        dto.setLanguages(extras.languages == null ? new ArrayList<>() : extras.languages);
        dto.setResumePdfUrl(extras.resumePdfUrl);
        dto.setResumePdfName(extras.resumePdfName);
        dto.setResumePdfPageCount(extras.resumePdfPageCount);
        dto.setResumePdfPageImageUrls(extras.resumePdfPageImageUrls == null ? new ArrayList<>() : extras.resumePdfPageImageUrls);
        String contactPhone = firstNonBlank(profile == null ? null : profile.getPhone(), user.getPhone(), "");
        dto.setHasContactPhone(StringUtils.hasText(contactPhone));
        dto.setContactPhone(includePrivate ? contactPhone : null);
        dto.setRealName(includePrivate ? firstNonBlank(profile == null ? null : profile.getRealName(), null, null) : null);
        dto.setIsCertified(resolveCertified(profile, user));
        dto.setCapabilitySummary(capabilityAccountService.actorLevelInfo(userId));
        return dto;
    }

    private boolean hasAiResumeApplyMeta(ActorProfileSaveDTO dto) {
        return dto != null
                && dto.getAiResumeApplyMeta() != null
                && StringUtils.hasText(dto.getAiResumeApplyMeta().getDraftId())
                && dto.getAiResumeApplyMeta().getAppliedPatchIds() != null
                && !dto.getAiResumeApplyMeta().getAppliedPatchIds().isEmpty();
    }

    private List<ActorWorkExperienceDTO> loadExperiences(Long userId) {
        return actorExperienceMapper.selectList(new LambdaQueryWrapper<ActorExperience>()
                        .eq(ActorExperience::getUserId, userId)
                        .orderByDesc(ActorExperience::getShootYear)
                        .orderByDesc(ActorExperience::getShootMonth)
                        .orderByDesc(ActorExperience::getSortNo)
                        .orderByDesc(ActorExperience::getExperienceId))
                .stream()
                .map(this::toExperienceDto)
                .toList();
    }

    private void syncExperiences(ActorProfile profile, List<ActorWorkExperienceDTO> experiences) {
        List<ActorExperience> existing = actorExperienceMapper.selectList(new LambdaQueryWrapper<ActorExperience>()
                .eq(ActorExperience::getUserId, profile.getUserId())
                .orderByDesc(ActorExperience::getSortNo)
                .orderByDesc(ActorExperience::getExperienceId));
        List<ActorWorkExperienceDTO> safeExperiences = safeList(experiences);
        if (safeExperiences.isEmpty()) {
            if (!existing.isEmpty()) {
                actorExperienceMapper.delete(new LambdaQueryWrapper<ActorExperience>()
                        .eq(ActorExperience::getUserId, profile.getUserId()));
            }
            return;
        }

        List<Long> retainedIds = new ArrayList<>();
        for (int i = 0; i < safeExperiences.size(); i++) {
            ActorWorkExperienceDTO item = safeExperiences.get(i);
            if (!StringUtils.hasText(item.getProjectName())) {
                continue;
            }
            ActorExperience experience = null;
            if (item.getId() != null) {
                for (ActorExperience current : existing) {
                    if (Objects.equals(current.getExperienceId(), item.getId())) {
                        experience = current;
                        retainedIds.add(current.getExperienceId());
                        break;
                    }
                }
            }
            boolean creating = experience == null;
            if (creating) {
                experience = new ActorExperience();
                experience.setUserId(profile.getUserId());
                experience.setActorProfileId(profile.getActorProfileId());
            } else if (experience.getActorProfileId() == null) {
                experience.setActorProfileId(profile.getActorProfileId());
            }
            experience.setDramaName(item.getProjectName().trim());
            experience.setRoleName(trimToNull(item.getRoleName()));
            experience.setShootYear(parseShootYear(item.getShootDate()));
            experience.setShootMonth(parseShootMonth(item.getShootDate()));
            experience.setRoleDesc(trimToNull(item.getDescription()));
            experience.setSortNo(safeExperiences.size() - i);
            experience.setExtendedField(writeJson(new ExperienceExtras(safeList(item.getPhotos()))));
            if (creating) {
                actorExperienceMapper.insert(experience);
            } else {
                actorExperienceMapper.updateById(experience);
            }
            item.setId(experience.getExperienceId());
        }

        if (!existing.isEmpty()) {
            List<Long> removeIds = existing.stream()
                    .map(ActorExperience::getExperienceId)
                    .filter(Objects::nonNull)
                    .filter(id -> !retainedIds.contains(id))
                    .toList();
            if (!removeIds.isEmpty()) {
                actorExperienceMapper.deleteBatchIds(removeIds);
            }
        }
    }

    private ActorWorkExperienceDTO toExperienceDto(ActorExperience item) {
        ExperienceExtras extras = readExperienceExtras(item.getExtendedField());
        ActorWorkExperienceDTO dto = new ActorWorkExperienceDTO();
        dto.setId(item.getExperienceId());
        dto.setProjectName(defaultText(item.getDramaName()));
        dto.setRoleName(defaultText(item.getRoleName()));
        dto.setShootDate(buildShootDate(item.getShootYear(), item.getShootMonth()));
        dto.setPhotos(extras.photos == null ? new ArrayList<>() : extras.photos);
        dto.setDescription(defaultText(item.getRoleDesc()));
        return dto;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }

    private boolean resolveCertified(ActorProfile profile, User user) {
        if (profile != null && profile.getIsCertified() != null) {
            return Boolean.TRUE.equals(profile.getIsCertified());
        }
        return user.getRealAuthStatus() != null && user.getRealAuthStatus() == 2;
    }

    private ProfileExtras buildProfileExtras(ActorProfileSaveDTO dto, ProfileExtras existing) {
        ProfileExtras extras = new ProfileExtras();
        extras.photoCategories = dto.getPhotoCategories() == null ? existing.photoCategories : dto.getPhotoCategories();
        extras.bodyType = dto.getBodyType() == null ? existing.bodyType : trimToNull(dto.getBodyType());
        extras.hairStyle = dto.getHairStyle() == null ? existing.hairStyle : trimToNull(dto.getHairStyle());
        extras.languages = dto.getLanguages() == null ? existing.languages : safeList(dto.getLanguages());
        extras.resumePdfUrl = dto.getResumePdfUrl() == null ? existing.resumePdfUrl : trimToNull(dto.getResumePdfUrl());
        extras.resumePdfName = dto.getResumePdfName() == null ? existing.resumePdfName : trimToNull(dto.getResumePdfName());
        extras.resumePdfPageCount = dto.getResumePdfPageCount() == null ? existing.resumePdfPageCount : dto.getResumePdfPageCount();
        extras.resumePdfPageImageUrls = dto.getResumePdfPageImageUrls() == null ? existing.resumePdfPageImageUrls : safeList(dto.getResumePdfPageImageUrls());
        return extras;
    }

    private ProfileExtras readProfileExtras(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new ProfileExtras();
        }
        try {
            return objectMapper.readValue(raw, ProfileExtras.class);
        } catch (Exception ignored) {
            return new ProfileExtras();
        }
    }

    private ExperienceExtras readExperienceExtras(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new ExperienceExtras(new ArrayList<>());
        }
        try {
            return objectMapper.readValue(raw, ExperienceExtras.class);
        } catch (Exception ignored) {
            return new ExperienceExtras(new ArrayList<>());
        }
    }

    private ActorPhotoCategoriesDTO resolvePhotoCategories(ActorPhotoCategoriesDTO categories, List<String> photos) {
        if (categories != null && (!categories.getPortrait().isEmpty()
                || !categories.getLifestyle().isEmpty()
                || !categories.getProduction().isEmpty())) {
            return categories;
        }
        List<String> safePhotos = safeList(photos);
        ActorPhotoCategoriesDTO dto = new ActorPhotoCategoriesDTO();
        dto.setPortrait(new ArrayList<>(safePhotos.subList(0, Math.min(3, safePhotos.size()))));
        dto.setLifestyle(new ArrayList<>(safePhotos.subList(Math.min(3, safePhotos.size()), Math.min(6, safePhotos.size()))));
        dto.setProduction(new ArrayList<>(safePhotos.subList(Math.min(6, safePhotos.size()), Math.min(9, safePhotos.size()))));
        return dto;
    }

    private List<String> readStringList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException("演员档案序列化失败");
        }
    }

    private List<String> splitCommaSeparated(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new ArrayList<>();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String writeCommaSeparated(List<String> values) {
        return safeList(values).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }

    private String writeStyleTags(ActorProfileSaveDTO dto) {
        List<String> tags = new ArrayList<>();
        if (StringUtils.hasText(dto.getBodyType())) {
            tags.add(dto.getBodyType().trim());
        }
        if (StringUtils.hasText(dto.getHairStyle())) {
            tags.add(dto.getHairStyle().trim());
        }
        tags.addAll(safeList(dto.getLanguages()));
        return writeCommaSeparated(tags);
    }

    private String writeExperienceSummary(List<ActorWorkExperienceDTO> experiences) {
        return safeList(experiences).stream()
                .filter(item -> StringUtils.hasText(item.getProjectName()) || StringUtils.hasText(item.getDescription()))
                .map(item -> StringUtils.hasText(item.getDescription()) ? item.getDescription().trim() : item.getProjectName().trim())
                .reduce((left, right) -> left + "；" + right)
                .orElse(null);
    }

    private Integer toGenderCode(String gender) {
        if ("male".equalsIgnoreCase(gender)) {
            return 1;
        }
        if ("female".equalsIgnoreCase(gender)) {
            return 2;
        }
        return 0;
    }

    private String toGenderText(Integer gender) {
        if (Objects.equals(gender, 1)) {
            return "male";
        }
        if (Objects.equals(gender, 2)) {
            return "female";
        }
        return "unknown";
    }

    private LocalDate parseBirthday(String birthday) {
        if (!StringUtils.hasText(birthday)) {
            return null;
        }
        try {
            return LocalDate.parse(birthday.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer parseShootYear(String shootDate) {
        if (!StringUtils.hasText(shootDate) || shootDate.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(shootDate.substring(0, 4));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer parseShootMonth(String shootDate) {
        if (!StringUtils.hasText(shootDate) || shootDate.length() < 7) {
            return null;
        }
        try {
            return Integer.parseInt(shootDate.substring(5, 7));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildShootDate(Integer year, Integer month) {
        if (year == null) {
            return "";
        }
        if (month == null || month <= 0) {
            return String.valueOf(year);
        }
        return String.format("%04d-%02d", year, month);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String first, String second, String defaultValue) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return defaultValue;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private static class ProfileExtras {
        public ActorPhotoCategoriesDTO photoCategories = new ActorPhotoCategoriesDTO();
        public String bodyType;
        public String hairStyle;
        public List<String> languages = new ArrayList<>();
        public String resumePdfUrl;
        public String resumePdfName;
        public Integer resumePdfPageCount;
        public List<String> resumePdfPageImageUrls = new ArrayList<>();
    }

    private static class ExperienceExtras {
        public List<String> photos = new ArrayList<>();

        public ExperienceExtras() {
        }

        public ExperienceExtras(List<String> photos) {
            this.photos = photos;
        }
    }
}
