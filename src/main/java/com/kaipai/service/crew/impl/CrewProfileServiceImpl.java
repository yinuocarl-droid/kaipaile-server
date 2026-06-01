package com.kaipai.service.crew.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.model.crew.dto.CrewProfileExtrasDTO;
import com.kaipai.model.crew.dto.CrewProfileRespDTO;
import com.kaipai.model.crew.dto.CrewProfileSaveDTO;
import com.kaipai.model.crew.entity.CrewProfile;
import com.kaipai.model.user.entity.User;
import com.kaipai.mapper.crew.CrewProfileMapper;
import com.kaipai.service.crew.CrewProfileService;
import com.kaipai.mapper.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CrewProfileServiceImpl extends ServiceImpl<CrewProfileMapper, CrewProfile> implements CrewProfileService {

    private static final Map<Integer, String> CREW_TYPE_LABELS = buildCrewTypeLabels();

    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public CrewProfileRespDTO mineProfile(Long currentUserId) {
        return toResp(requireOrCreateProfile(currentUserId), requireUser(currentUserId));
    }

    @Override
    public CrewProfileRespDTO profile(Long userId) {
        CrewProfile profile = findProfile(userId);
        return toResp(profile == null ? emptyProfile(userId) : profile, requireUser(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveProfile(Long currentUserId, CrewProfileSaveDTO dto) {
        User user = requireUser(currentUserId);
        CrewProfile profile = requireOrCreateProfile(currentUserId);
        CrewProfileExtrasDTO extras = readExtras(profile.getExtendedField());

        profile.setCrewName(trimToNull(dto.getCrewName()));
        profile.setContactName(trimToNull(dto.getContactName()));
        profile.setContactPhone(trimToNull(dto.getContactPhone()));
        profile.setIntro(trimToNull(dto.getRemark()));
        profile.setLogoUrl(trimToNull(dto.getAvatar()));
        profile.setLocationCity(trimToNull(dto.getLocation()));
        profile.setAddress(trimToNull(dto.getOfficeAddress()));
        profile.setCrewType(toCrewTypeCode(dto.getCrewType()));
        profile.setBusinessScope(trimToNull(dto.getFocusDirection()));
        profile.setCooperationTag(trimToNull(dto.getCooperationNeed()));

        extras.setCrewType(trimToNull(dto.getCrewType()));
        extras.setTeamScale(trimToNull(dto.getTeamScale()));
        extras.setFocusDirection(trimToNull(dto.getFocusDirection()));
        extras.setRepresentativeWorks(trimToNull(dto.getRepresentativeWorks()));
        extras.setCooperationNeed(trimToNull(dto.getCooperationNeed()));
        extras.setOfficeAddress(trimToNull(dto.getOfficeAddress()));
        profile.setExtendedField(writeExtras(extras));

        updateById(profile);

        User update = new User();
        update.setUserId(currentUserId);
        update.setUserName(firstNonBlank(dto.getCrewName(), user.getUserName(), null));
        update.setAvatarUrl(firstNonBlank(dto.getAvatar(), user.getAvatarUrl(), null));
        update.setUpdateUserId(currentUserId);
        update.setUpdateUserName(firstNonBlank(dto.getCrewName(), user.getUserName(), "system"));
        userMapper.updateById(update);
    }

    private CrewProfile requireOrCreateProfile(Long userId) {
        CrewProfile profile = findProfile(userId);
        if (profile != null) {
            return profile;
        }

        CrewProfile created = emptyProfile(userId);
        save(created);
        return created;
    }

    private CrewProfile findProfile(Long userId) {
        return getOne(new LambdaQueryWrapper<CrewProfile>()
                .eq(CrewProfile::getUserId, userId)
                .last("limit 1"), false);
    }

    private CrewProfile emptyProfile(Long userId) {
        CrewProfile profile = new CrewProfile();
        profile.setUserId(userId);
        profile.setCrewName("");
        profile.setCrewStatus(1);
        profile.setExtendedField(writeExtras(new CrewProfileExtrasDTO()));
        return profile;
    }

    private CrewProfileRespDTO toResp(CrewProfile profile, User user) {
        CrewProfileExtrasDTO extras = readExtras(profile.getExtendedField());
        CrewProfileRespDTO dto = new CrewProfileRespDTO();
        dto.setUserId(profile.getUserId());
        dto.setAvatar(firstNonBlank(profile.getLogoUrl(), user == null ? null : user.getAvatarUrl(), ""));
        dto.setCrewName(defaultText(profile.getCrewName()));
        dto.setContactName(defaultText(profile.getContactName()));
        dto.setContactPhone(firstNonBlank(profile.getContactPhone(), user == null ? null : user.getPhone(), ""));
        dto.setRemark(defaultText(profile.getIntro()));
        dto.setLocation(firstNonBlank(profile.getLocationCity(), profile.getLocationProvince(), profile.getAddress(), ""));
        dto.setCrewType(firstNonBlank(extras.getCrewType(), CREW_TYPE_LABELS.get(profile.getCrewType()), ""));
        dto.setTeamScale(defaultText(extras.getTeamScale()));
        dto.setFocusDirection(firstNonBlank(extras.getFocusDirection(), profile.getBusinessScope(), ""));
        dto.setRepresentativeWorks(defaultText(extras.getRepresentativeWorks()));
        dto.setCooperationNeed(firstNonBlank(extras.getCooperationNeed(), profile.getCooperationTag(), ""));
        dto.setOfficeAddress(firstNonBlank(extras.getOfficeAddress(), profile.getAddress(), ""));
        return dto;
    }

    private CrewProfileExtrasDTO readExtras(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new CrewProfileExtrasDTO();
        }
        try {
            return objectMapper.readValue(raw, CrewProfileExtrasDTO.class);
        } catch (Exception ignored) {
            return new CrewProfileExtrasDTO();
        }
    }

    private String writeExtras(CrewProfileExtrasDTO extras) {
        try {
            return objectMapper.writeValueAsString(extras == null ? new CrewProfileExtrasDTO() : extras);
        } catch (Exception e) {
            throw new BizException("团队资料序列化失败");
        }
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }

    private Integer toCrewTypeCode(String crewType) {
        String normalized = trimToNull(crewType);
        if (normalized == null) {
            return null;
        }
        return CREW_TYPE_LABELS.entrySet().stream()
                .filter(entry -> normalized.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static Map<Integer, String> buildCrewTypeLabels() {
        Map<Integer, String> labels = new LinkedHashMap<>();
        labels.put(1, "影视制作团队");
        labels.put(2, "短剧厂牌");
        labels.put(3, "广告内容团队");
        labels.put(4, "MCN内容团队");
        labels.put(5, "品牌影像团队");
        return labels;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        if (values == null || values.length == 0) {
            return null;
        }
        return values[values.length - 1];
    }
}
