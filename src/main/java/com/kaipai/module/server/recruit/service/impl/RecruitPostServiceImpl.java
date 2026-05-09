package com.kaipai.module.server.recruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.crew.dto.CrewProfileExtrasDTO;
import com.kaipai.module.model.crew.entity.CrewProfile;
import com.kaipai.module.model.recruit.dto.ProjectRespDTO;
import com.kaipai.module.model.recruit.dto.RecruitRoleQueryDTO;
import com.kaipai.module.model.recruit.dto.RecruitRoleRespDTO;
import com.kaipai.module.model.recruit.dto.RoleExtraDTO;
import com.kaipai.module.model.recruit.entity.RecruitPost;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.crew.mapper.CrewProfileMapper;
import com.kaipai.module.server.recruit.mapper.RecruitPostMapper;
import com.kaipai.module.server.recruit.service.RecruitPostService;
import com.kaipai.module.server.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecruitPostServiceImpl extends ServiceImpl<RecruitPostMapper, RecruitPost> implements RecruitPostService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CrewProfileMapper crewProfileMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<RecruitRoleRespDTO> searchRoles(RecruitRoleQueryDTO query) {
        long pageNo = query.getPage() == null || query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() == null || query.getSize() <= 0 ? 20 : query.getSize();
        Page<RecruitPost> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<RecruitPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RecruitPost::getPostStatus, 1)
                .and(inner -> inner.isNull(RecruitPost::getApplyDeadline).or().ge(RecruitPost::getApplyDeadline, LocalDateTime.now()));

        Integer genderCode = toGenderCode(query.getGender());
        if (genderCode != null) {
            wrapper.eq(RecruitPost::getRequireGender, genderCode);
        }
        if (query.getMinAge() != null) {
            wrapper.and(inner -> inner.isNull(RecruitPost::getRequireAgeMax).or().ge(RecruitPost::getRequireAgeMax, query.getMinAge()));
        }
        if (query.getMaxAge() != null) {
            wrapper.and(inner -> inner.isNull(RecruitPost::getRequireAgeMin).or().le(RecruitPost::getRequireAgeMin, query.getMaxAge()));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(inner -> inner.like(RecruitPost::getTitle, keyword)
                    .or()
                    .like(RecruitPost::getDramaName, keyword)
                    .or()
                    .like(RecruitPost::getRoleName, keyword)
                    .or()
                    .like(RecruitPost::getRoleDesc, keyword)
                    .or()
                    .like(RecruitPost::getRequireStyleTag, keyword));
        }
        wrapper.orderByDesc(RecruitPost::getPostType)
                .orderByDesc(RecruitPost::getLastUpdate)
                .orderByDesc(RecruitPost::getRecruitPostId);

        Page<RecruitPost> result = page(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.empty();
        }

        Map<Long, CrewProfile> crewMap = loadCrewMap(result.getRecords());
        Map<Long, CrewProfile> latestCrewByUserId = loadLatestCrewMapByUserId(result.getRecords());
        Map<Long, User> ownerMap = loadOwnerMap(result.getRecords());
        List<RecruitRoleRespDTO> list = result.getRecords().stream()
                .map(item -> {
                    CrewProfile crew = resolveCrewProfile(item, crewMap, latestCrewByUserId);
                    return toRole(item, crew, ownerMap.get(item.getUserId()));
                })
                .toList();
        return new PageResult<>(result.getTotal(), list);
    }

    @Override
    public RecruitRoleRespDTO detail(Long roleId) {
        RecruitPost post = getById(roleId);
        if (post == null) {
            throw new BizException("角色不存在");
        }
        Map<Long, CrewProfile> crewMap = new LinkedHashMap<>();
        if (post.getCrewProfileId() != null) {
            CrewProfile crew = crewProfileMapper.selectById(post.getCrewProfileId());
            if (crew != null) {
                crewMap.put(post.getCrewProfileId(), crew);
            }
        }
        CrewProfile crew = resolveCrewProfile(post, crewMap, loadLatestCrewMapByUserId(List.of(post)));
        User owner = post.getUserId() == null ? null : userMapper.selectById(post.getUserId());
        return toRole(post, crew, owner);
    }

    private Map<Long, CrewProfile> loadCrewMap(List<RecruitPost> posts) {
        Set<Long> crewIds = posts.stream()
                .map(RecruitPost::getCrewProfileId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (crewIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return crewProfileMapper.selectBatchIds(crewIds).stream()
                .collect(Collectors.toMap(CrewProfile::getCrewProfileId, item -> item, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, User> loadOwnerMap(List<RecruitPost> posts) {
        Set<Long> userIds = posts.stream()
                .map(RecruitPost::getUserId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, item -> item, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, CrewProfile> loadLatestCrewMapByUserId(List<RecruitPost> posts) {
        Set<Long> userIds = posts.stream()
                .map(RecruitPost::getUserId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return crewProfileMapper.selectList(new LambdaQueryWrapper<CrewProfile>()
                        .in(CrewProfile::getUserId, userIds)
                        .orderByDesc(CrewProfile::getLastUpdate)
                        .orderByDesc(CrewProfile::getCrewProfileId))
                .stream()
                .collect(Collectors.toMap(CrewProfile::getUserId, item -> item, (left, right) -> left, LinkedHashMap::new));
    }

    private CrewProfile resolveCrewProfile(RecruitPost post,
                                                 Map<Long, CrewProfile> crewMap,
                                                 Map<Long, CrewProfile> latestCrewByUserId) {
        if (post == null) {
            return null;
        }
        CrewProfile crew = post.getCrewProfileId() == null ? null : crewMap.get(post.getCrewProfileId());
        if (crew != null) {
            return crew;
        }
        return post.getUserId() == null ? null : latestCrewByUserId.get(post.getUserId());
    }

    private RecruitRoleRespDTO toRole(RecruitPost post, CrewProfile crew, User owner) {
        RoleExtraDTO roleExtra = readRoleExtra(post.getExtendedField());
        CrewProfileExtrasDTO crewExtras = readCrewExtras(crew == null ? null : crew.getExtendedField());
        ProjectRespDTO project = findProject(crewExtras, roleExtra.getProjectId());

        RecruitRoleRespDTO dto = new RecruitRoleRespDTO();
        dto.setId(post.getRecruitPostId());
        dto.setProjectId(project == null || project.getId() == null ? post.getRecruitPostId() : project.getId());
        dto.setRoleName(defaultText(post.getRoleName()));
        dto.setGender(toGenderText(post.getRequireGender()));
        dto.setMinAge(defaultInt(post.getRequireAgeMin(), 18));
        dto.setMaxAge(defaultInt(post.getRequireAgeMax(), 35));
        dto.setRequirement(requireFirstText("角色要求缺失", post.getRoleDesc(), post.getTitle()));
        dto.setFee(resolveSalary(post));
        dto.setDeadline(formatDate(post.getApplyDeadline()));
        dto.setStatus(resolveRoleStatus(post.getPostStatus()));
        dto.setTags(mergeTags(roleExtra.getTags(), splitCommaSeparated(post.getRequireStyleTag())));
        dto.setPublishTime(formatDateTime(post.getLastUpdate() != null ? post.getLastUpdate() : post.getCreateTime()));
        dto.setCoverImage(firstNonBlank(roleExtra.getCoverImage(),
                project == null ? null : project.getCoverImage(),
                crew == null ? null : crew.getCoverUrl(),
                crew == null ? null : crew.getLogoUrl(),
                null));
        dto.setProject(buildProject(post, crew, owner, project));
        dto.setCrew(buildCrew(post, crew, owner, crewExtras));
        return dto;
    }

    private RecruitRoleRespDTO.ProjectDTO buildProject(RecruitPost post, CrewProfile crew, User owner, ProjectRespDTO projectSource) {
        RecruitRoleRespDTO.ProjectDTO project = new RecruitRoleRespDTO.ProjectDTO();
        project.setId(projectSource == null || projectSource.getId() == null ? post.getRecruitPostId() : projectSource.getId());
        project.setCrewId(projectSource == null || projectSource.getCrewId() == null
                ? (crew != null && crew.getUserId() != null ? crew.getUserId() : post.getUserId())
                : projectSource.getCrewId());
        project.setTitle(requireFirstText("项目名称缺失", projectSource == null ? null : projectSource.getTitle(), post.getDramaName(), post.getTitle()));
        project.setDescription(firstNonBlank(projectSource == null ? null : projectSource.getDescription(),
                post.getTitle(),
                crew == null ? null : crew.getIntro(),
                post.getRoleDesc()));
        project.setLocation(firstNonBlank(projectSource == null ? null : projectSource.getLocation(),
                resolveLocation(post.getShootProvince(), post.getShootCity(), post.getShootAddress()),
                "地点待定"));
        project.setStatus(projectSource == null || projectSource.getStatus() == null
                ? resolveProjectStatus(post.getPostStatus())
                : projectSource.getStatus());
        project.setType(firstNonBlank(projectSource == null ? null : projectSource.getType(),
                post.getPostType() != null && post.getPostType() == 2 ? "紧急招募" : "角色招募"));
        project.setShootingDate(firstNonBlank(projectSource == null ? null : projectSource.getShootingDate(),
                resolveShootingDate(post.getShootStartTime(), post.getShootEndTime())));
        project.setRoleCount(projectSource != null && projectSource.getRoleCount() != null && projectSource.getRoleCount() > 0
                ? projectSource.getRoleCount()
                : 1);
        project.setCoverImage(firstNonBlank(projectSource == null ? null : projectSource.getCoverImage(),
                crew == null ? null : crew.getCoverUrl(),
                crew == null ? null : crew.getLogoUrl(),
                owner == null ? null : owner.getAvatarUrl()));
        return project;
    }

    private RecruitRoleRespDTO.CrewDTO buildCrew(RecruitPost post,
                                                       CrewProfile crew,
                                                       User owner,
                                                       CrewProfileExtrasDTO extras) {
        RecruitRoleRespDTO.CrewDTO dto = new RecruitRoleRespDTO.CrewDTO();
        dto.setUserId(crew != null && crew.getUserId() != null ? crew.getUserId() : post.getUserId());
        dto.setAvatar(firstNonBlank(crew == null ? null : crew.getLogoUrl(), owner == null ? null : owner.getAvatarUrl(), null));
        dto.setCrewName(firstNonBlank(crew == null ? null : crew.getCrewName(), owner == null ? null : owner.getUserName(), "精选剧组"));
        dto.setContactName(firstNonBlank(post.getContactName(), crew == null ? null : crew.getContactName(), null));
        dto.setContactPhone(firstNonBlank(post.getContactPhone(), crew == null ? null : crew.getContactPhone(), owner == null ? null : owner.getPhone()));
        dto.setRemark(firstNonBlank(crew == null ? null : crew.getIntro(),
                extras == null ? null : extras.getFocusDirection(),
                crew == null ? null : crew.getBusinessScope(),
                "专注影视与内容项目合作。"));
        dto.setLocation(resolveLocation(crew == null ? null : crew.getLocationProvince(), crew == null ? null : crew.getLocationCity(), crew == null ? null : crew.getAddress()));
        dto.setCrewType(firstNonBlank(extras == null ? null : extras.getCrewType(), resolveCrewType(crew == null ? null : crew.getCrewType()), null));
        dto.setTeamScale(extras == null ? null : extras.getTeamScale());
        dto.setFocusDirection(firstNonBlank(extras == null ? null : extras.getFocusDirection(), crew == null ? null : crew.getBusinessScope(), null));
        dto.setRepresentativeWorks(extras == null ? null : extras.getRepresentativeWorks());
        dto.setCooperationNeed(firstNonBlank(extras == null ? null : extras.getCooperationNeed(), crew == null ? null : crew.getCooperationTag(), null));
        dto.setOfficeAddress(firstNonBlank(extras == null ? null : extras.getOfficeAddress(), crew == null ? null : crew.getAddress(), null));
        return dto;
    }

    private Integer toGenderCode(String gender) {
        if (!StringUtils.hasText(gender) || "不限".equals(gender.trim())) {
            return null;
        }
        if ("男".equals(gender.trim()) || "male".equalsIgnoreCase(gender.trim())) {
            return 1;
        }
        if ("女".equals(gender.trim()) || "female".equalsIgnoreCase(gender.trim())) {
            return 2;
        }
        return null;
    }

    private String toGenderText(Integer gender) {
        if (gender != null && gender == 1) {
            return "男";
        }
        if (gender != null && gender == 2) {
            return "女";
        }
        return "不限";
    }

    private String resolveSalary(RecruitPost post) {
        if (!StringUtils.hasText(post.getSalary()) || Boolean.FALSE.equals(post.getSalaryVisible())) {
            return "面议";
        }
        return post.getSalary().trim();
    }

    private String resolveRoleStatus(Integer postStatus) {
        if (postStatus != null && postStatus == 1) {
            return "recruiting";
        }
        if (postStatus != null && postStatus == 2) {
            return "closed";
        }
        return "paused";
    }

    private Integer resolveProjectStatus(Integer postStatus) {
        return postStatus != null && postStatus == 1 ? 1 : 2;
    }

    private List<String> splitCommaSeparated(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> mergeTags(List<String> extraTags, List<String> styleTags) {
        Set<String> values = new LinkedHashSet<>();
        if (extraTags != null) {
            values.addAll(extraTags.stream().filter(StringUtils::hasText).map(String::trim).toList());
        }
        if (styleTags != null) {
            values.addAll(styleTags.stream().filter(StringUtils::hasText).map(String::trim).toList());
        }
        return new java.util.ArrayList<>(values);
    }

    private String resolveLocation(String province, String city, String address) {
        String primary = StringUtils.hasText(city) ? city.trim() : StringUtils.hasText(province) ? province.trim() : "";
        if (StringUtils.hasText(primary)) {
            return primary;
        }
        return StringUtils.hasText(address) ? address.trim() : "地点待定";
    }

    private String resolveShootingDate(LocalDateTime start, LocalDateTime end) {
        if (start == null && end == null) {
            return "待定档期";
        }
        if (start != null && end != null) {
            String startText = start.format(DATE_FORMATTER);
            String endText = end.format(DATE_FORMATTER);
            if (startText.equals(endText)) {
                return startText;
            }
            return startText + " - " + endText;
        }
        return (start != null ? start : end).format(DATE_FORMATTER);
    }

    private String resolveCrewType(Integer crewType) {
        if (crewType != null && crewType == 1) {
            return "影视制作团队";
        }
        if (crewType != null && crewType == 2) {
            return "短剧厂牌";
        }
        if (crewType != null && crewType == 3) {
            return "广告内容团队";
        }
        if (crewType != null && crewType == 4) {
            return "MCN内容团队";
        }
        if (crewType != null && crewType == 5) {
            return "品牌影像团队";
        }
        return null;
    }

    private CrewProfileExtrasDTO readCrewExtras(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new CrewProfileExtrasDTO();
        }
        try {
            CrewProfileExtrasDTO extras = objectMapper.readValue(raw, CrewProfileExtrasDTO.class);
            if (extras.getProjects() == null) {
                extras.setProjects(new java.util.ArrayList<>());
            }
            return extras;
        } catch (Exception ignored) {
            return new CrewProfileExtrasDTO();
        }
    }

    private RoleExtraDTO readRoleExtra(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new RoleExtraDTO();
        }
        try {
            RoleExtraDTO extra = objectMapper.readValue(raw, RoleExtraDTO.class);
            if (extra.getTags() == null) {
                extra.setTags(new java.util.ArrayList<>());
            }
            return extra;
        } catch (Exception ignored) {
            return new RoleExtraDTO();
        }
    }

    private ProjectRespDTO findProject(CrewProfileExtrasDTO extras, Long projectId) {
        if (extras == null || extras.getProjects() == null || projectId == null) {
            return null;
        }
        return extras.getProjects().stream()
                .filter(java.util.Objects::nonNull)
                .filter(item -> java.util.Objects.equals(item.getId(), projectId))
                .findFirst()
                .map(this::copyProject)
                .orElse(null);
    }

    private ProjectRespDTO copyProject(ProjectRespDTO source) {
        ProjectRespDTO target = new ProjectRespDTO();
        if (source == null) {
            return target;
        }
        target.setId(source.getId());
        target.setCrewId(source.getCrewId());
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setLocation(source.getLocation());
        target.setStatus(source.getStatus());
        target.setType(source.getType());
        target.setShootingDate(source.getShootingDate());
        target.setRoleCount(source.getRoleCount());
        target.setCoverImage(source.getCoverImage());
        return target;
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_FORMATTER);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMATTER);
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
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

    private String requireFirstText(String errorMessage, String... values) {
        String value = firstNonBlank(values);
        if (!StringUtils.hasText(value)) {
            throw new BizException(errorMessage);
        }
        return value;
    }
}
