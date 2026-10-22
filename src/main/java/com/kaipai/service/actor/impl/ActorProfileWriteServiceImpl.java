package com.kaipai.service.actor.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.mapper.actor.ActorProfileMapper;
import com.kaipai.mapper.user.UserMapper;
import com.kaipai.model.user.entity.User;
import com.kaipai.common.exception.BizException;
import com.kaipai.model.actor.dto.ActorProfileCareerUpdateDTO;
import com.kaipai.model.actor.dto.ActorProfileCoreUpdateDTO;
import com.kaipai.model.actor.dto.ActorProfileIntroUpdateDTO;
import com.kaipai.model.actor.dto.ActorProfileMineUpdateDTO;
import com.kaipai.model.actor.dto.ActorProfileRespDTO;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.actor.entity.ActorProfile;
import com.kaipai.service.actor.ActorMediaAssetOwnershipVerifier;
import com.kaipai.service.actor.ActorProfileWriteService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ActorProfileWriteServiceImpl implements ActorProfileWriteService {

    private final ActorProfileMapper profileMapper;
    private final ActorMediaAssetOwnershipVerifier assetOwnershipVerifier;
    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;

    @Override
    public ActorProfileRespDTO mine(Long currentUserId) {
        ActorProfile profile = findMine(currentUserId);
        if (profile == null) {
            profile = newProfileDraft(currentUserId);
        }
        return toResponse(profile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActorProfileRespDTO saveMine(Long currentUserId, ActorProfileMineUpdateDTO request) {
        if (request.getNickname() != null
                && (!StringUtils.hasText(request.getNickname()) || request.getNickname().length() > 64)) {
            throw new BizException("昵称须为1至64个字符");
        }
        ActorProfile profile = findMine(currentUserId);
        boolean creating = profile == null;
        if (creating) {
            if (request.getExpectedProfileVersion() != 0) {
                throw ProfileDomainErrorCode.PROFILE_IMPORT_CONTEXT_VERSION_CONFLICT.toException();
            }
            profile = newProfileDraft(currentUserId);
            profile.setProfileStatus(1);
        } else if (!request.getExpectedProfileVersion().equals(profile.getVersion())) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_CONTEXT_VERSION_CONFLICT.toException();
        }

        assetOwnershipVerifier.requireOwnedReadyPhoto(currentUserId, request.getAvatarAssetId());
        applyCore(profile, request.getCore());
        applyCareer(profile, request.getCareer());
        profile.setAvatarAssetId(request.getAvatarAssetId());
        profile.setIntro(trimToNull(request.getIntro()));

        Integer previousVersion = profile.getVersion();
        int affectedRows = creating ? profileMapper.insert(profile) : profileMapper.updateById(profile);
        if (affectedRows != 1) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_CONTEXT_VERSION_CONFLICT.toException();
        }
        if (!creating && Objects.equals(previousVersion, profile.getVersion())) {
            profile.setVersion(previousVersion + 1);
        }
        if (request.getNickname() != null) {
            String nickname = request.getNickname().trim();
            User user = userMapper.selectById(currentUserId);
            if (user == null) throw new BizException("用户不存在");
            if (!nickname.equals(user.getUserName())) {
                User update = new User();
                update.setUserId(currentUserId);
                update.setUserName(nickname);
                update.setUpdateUserId(currentUserId);
                update.setUpdateUserName(nickname);
                if (userMapper.updateById(update) != 1) {
                    throw new BizException("昵称保存失败，请重试");
                }
            }
        }
        return toResponse(profile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActorProfileRespDTO saveIntro(Long currentUserId, ActorProfileIntroUpdateDTO request) {
        ActorProfile profile = findMine(currentUserId);
        boolean creating = profile == null;
        if (creating) {
            if (request.getExpectedProfileVersion() != 0) {
                throw ProfileDomainErrorCode.PROFILE_IMPORT_CONTEXT_VERSION_CONFLICT.toException();
            }
            profile = newProfileDraft(currentUserId);
        } else if (!request.getExpectedProfileVersion().equals(profile.getVersion())) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_CONTEXT_VERSION_CONFLICT.toException();
        }

        profile.setIntro(trimToNull(request.getIntro()));
        Integer previousVersion = profile.getVersion();
        int affectedRows = creating ? profileMapper.insert(profile) : profileMapper.updateById(profile);
        if (affectedRows != 1) {
            throw ProfileDomainErrorCode.PROFILE_IMPORT_CONTEXT_VERSION_CONFLICT.toException();
        }
        if (!creating && Objects.equals(previousVersion, profile.getVersion())) {
            profile.setVersion(previousVersion + 1);
        }
        return toResponse(profile);
    }

    private ActorProfile newProfileDraft(Long currentUserId) {
        ActorProfile profile = new ActorProfile();
        profile.setUserId(currentUserId);
        profile.setVersion(0);
        profile.setWorkLibraryVersion(0L);
        profile.setProfileStatus(3);
        return profile;
    }

    private ActorProfile findMine(Long currentUserId) {
        return profileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getUserId, currentUserId)
                .last("limit 1"));
    }

    private void applyCore(ActorProfile profile, ActorProfileCoreUpdateDTO core) {
        profile.setNickName(core.getPublicName().trim());
        profile.setGender("female".equals(core.getGender()) ? 2 : 1);
        profile.setAge(core.getAge());
        profile.setHeight(core.getHeight());
        profile.setLocationCity(core.getCurrentCity().trim());
    }

    private void applyCareer(ActorProfile profile, ActorProfileCareerUpdateDTO career) {
        profile.setWeight(career.getWeight());
        profile.setOriginPlace(trimToNull(career.getOriginPlace()));
        profile.setSchoolName(trimToNull(career.getSchoolName()));
        profile.setMajorName(trimToNull(career.getMajorName()));
        profile.setLanguageTagsJson(writeTags(career.getLanguageTags()));
        profile.setSpecialtyTagsJson(writeTags(career.getSpecialtyTags()));
        profile.setRoleTypeTagsJson(writeTags(career.getRoleTypeTags()));
        profile.setProfessionalAbilityTagsJson(writeTags(career.getProfessionalAbilityTags()));
    }

    private ActorProfileRespDTO toResponse(ActorProfile profile) {
        ActorProfileRespDTO response = new ActorProfileRespDTO();
        response.setActorProfileId(profile.getActorProfileId());
        response.setUserId(profile.getUserId());
        response.setProfileVersion(profile.getVersion());
        response.setWorkLibraryVersion(profile.getWorkLibraryVersion() == null ? 0L : profile.getWorkLibraryVersion());
        response.setAvatarAssetId(profile.getAvatarAssetId());
        User user = userMapper.selectById(profile.getUserId());
        response.setNickname(user == null ? null : user.getUserName());
        response.setPublicName(profile.getNickName());
        response.setGender(profile.getGender() == null || profile.getGender() == 0
                ? null
                : profile.getGender() == 2 ? "female" : "male");
        response.setAge(profile.getAge());
        response.setHeight(profile.getHeight());
        response.setCurrentCity(profile.getLocationCity());
        response.setWeight(profile.getWeight());
        response.setOriginPlace(profile.getOriginPlace());
        response.setSchoolName(profile.getSchoolName());
        response.setMajorName(profile.getMajorName());
        response.setLanguageTags(readTags(profile.getLanguageTagsJson()));
        response.setSpecialtyTags(readTags(profile.getSpecialtyTagsJson()));
        response.setRoleTypeTags(readTags(profile.getRoleTypeTagsJson()));
        response.setProfessionalAbilityTags(readTags(profile.getProfessionalAbilityTagsJson()));
        response.setIntro(profile.getIntro());
        return response;
    }

    private String writeTags(List<String> tags) {
        try {
            List<String> normalized = tags == null ? List.of() : tags.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception error) {
            throw new IllegalStateException("profile career tags serialization failed", error);
        }
    }

    private List<String> readTags(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception error) {
            throw new IllegalStateException("profile career tags deserialization failed", error);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
