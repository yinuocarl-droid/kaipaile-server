package com.kaipai.module.server.recruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.actor.entity.ActorProfile;
import com.kaipai.module.model.company.dto.CompanyProfileExtrasDTO;
import com.kaipai.module.model.company.entity.CompanyProfile;
import com.kaipai.module.model.recruit.dto.AdminRecruitApplyListItemDTO;
import com.kaipai.module.model.recruit.dto.AdminRecruitApplyQueryDTO;
import com.kaipai.module.model.recruit.dto.AdminRecruitProjectListItemDTO;
import com.kaipai.module.model.recruit.dto.AdminRecruitProjectQueryDTO;
import com.kaipai.module.model.recruit.dto.AdminRecruitProjectStatusChangeDTO;
import com.kaipai.module.model.recruit.dto.AdminRecruitRoleListItemDTO;
import com.kaipai.module.model.recruit.dto.AdminRecruitRoleQueryDTO;
import com.kaipai.module.model.recruit.dto.AdminRecruitRoleStatusChangeDTO;
import com.kaipai.module.model.recruit.dto.ProjectRespDTO;
import com.kaipai.module.model.recruit.dto.RoleExtraDTO;
import com.kaipai.module.model.recruit.entity.RecruitApply;
import com.kaipai.module.model.recruit.entity.RecruitPost;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.actor.mapper.ActorProfileMapper;
import com.kaipai.module.server.company.mapper.CompanyProfileMapper;
import com.kaipai.module.server.recruit.mapper.RecruitApplyMapper;
import com.kaipai.module.server.recruit.mapper.RecruitPostMapper;
import com.kaipai.module.server.recruit.service.AdminRecruitGovernanceService;
import com.kaipai.module.server.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminRecruitGovernanceServiceImpl implements AdminRecruitGovernanceService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CompanyProfileMapper companyProfileMapper;
    private final RecruitPostMapper recruitPostMapper;
    private final RecruitApplyMapper recruitApplyMapper;
    private final UserMapper userMapper;
    private final ActorProfileMapper actorProfileMapper;
    private final ObjectMapper objectMapper;
    private final AdminOperationLogger adminOperationLogger;

    @Override
    public PageResult<AdminRecruitProjectListItemDTO> projectList(AdminRecruitProjectQueryDTO query) {
        List<CompanyProfile> profiles = companyProfileMapper.selectList(new LambdaQueryWrapper<CompanyProfile>()
                .orderByDesc(CompanyProfile::getLastUpdate)
                .orderByDesc(CompanyProfile::getCompanyProfileId));
        if (profiles.isEmpty()) {
            return PageResult.empty();
        }

        Map<Long, User> crewUserMap = loadUserMap(profiles.stream()
                .map(CompanyProfile::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        List<AdminRecruitProjectListItemDTO> rows = profiles.stream()
                .flatMap(profile -> safeProjects(readCompanyExtras(profile.getExtendedField())).stream()
                        .map(project -> toProjectItem(profile, crewUserMap.get(profile.getUserId()), project)))
                .filter(item -> matchesProject(item, query))
                .sorted(Comparator.comparing(AdminRecruitProjectListItemDTO::getSourceUpdatedAt,
                                Comparator.nullsLast(String::compareTo)).reversed()
                        .thenComparing(AdminRecruitProjectListItemDTO::getProjectId,
                                Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();

        return paginate(rows, query == null ? 1 : query.getPageNo(), query == null ? 20 : query.getPageSize());
    }

    @Override
    public PageResult<AdminRecruitRoleListItemDTO> roleList(AdminRecruitRoleQueryDTO query) {
        LambdaQueryWrapper<RecruitPost> wrapper = new LambdaQueryWrapper<>();
        if (query != null && query.getRoleId() != null) {
            wrapper.eq(RecruitPost::getRecruitPostId, query.getRoleId());
        }
        if (query != null && query.getCrewUserId() != null) {
            wrapper.eq(RecruitPost::getUserId, query.getCrewUserId());
        }
        Integer postStatus = toRecruitPostStatus(query == null ? null : query.getStatus());
        if (postStatus != null) {
            wrapper.eq(RecruitPost::getPostStatus, postStatus);
        }
        if (StringUtils.hasText(query == null ? null : query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(inner -> inner.like(RecruitPost::getTitle, keyword)
                    .or()
                    .like(RecruitPost::getDramaName, keyword)
                    .or()
                    .like(RecruitPost::getRoleName, keyword)
                    .or()
                    .like(RecruitPost::getRoleDesc, keyword));
        }
        wrapper.orderByDesc(RecruitPost::getLastUpdate)
                .orderByDesc(RecruitPost::getRecruitPostId);

        List<RecruitPost> posts = recruitPostMapper.selectList(wrapper);
        if (posts.isEmpty()) {
            return PageResult.empty();
        }

        Map<Long, CompanyProfile> companyProfileMap = loadCompanyProfileMap(posts.stream()
                .map(RecruitPost::getCompanyProfileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, User> crewUserMap = loadUserMap(posts.stream()
                .map(RecruitPost::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, Map<Long, ProjectRespDTO>> projectMapByCrewUserId = buildProjectMapByCrewUserId(companyProfileMap.values());

        List<AdminRecruitRoleListItemDTO> rows = posts.stream()
                .map(post -> toRoleItem(
                        post,
                        companyProfileMap.get(post.getCompanyProfileId()),
                        crewUserMap.get(post.getUserId()),
                        projectMapByCrewUserId.getOrDefault(post.getUserId(), Collections.emptyMap())))
                .filter(item -> matchesRole(item, query))
                .sorted(Comparator.comparing(AdminRecruitRoleListItemDTO::getPublishTime,
                                Comparator.nullsLast(String::compareTo)).reversed()
                        .thenComparing(AdminRecruitRoleListItemDTO::getRoleId,
                                Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();

        return paginate(rows, query == null ? 1 : query.getPageNo(), query == null ? 20 : query.getPageSize());
    }

    @Override
    public PageResult<AdminRecruitApplyListItemDTO> applyList(AdminRecruitApplyQueryDTO query) {
        LambdaQueryWrapper<RecruitApply> wrapper = new LambdaQueryWrapper<>();
        if (query != null && query.getApplyId() != null) {
            wrapper.eq(RecruitApply::getRecruitApplyId, query.getApplyId());
        }
        if (query != null && query.getRoleId() != null) {
            wrapper.eq(RecruitApply::getRecruitPostId, query.getRoleId());
        }
        if (query != null && query.getActorUserId() != null) {
            wrapper.eq(RecruitApply::getActorUserId, query.getActorUserId());
        }
        applyStatusFilter(wrapper, query == null ? null : query.getStatus());
        wrapper.orderByDesc(RecruitApply::getCreateTime)
                .orderByDesc(RecruitApply::getRecruitApplyId);

        List<RecruitApply> applies = recruitApplyMapper.selectList(wrapper);
        if (applies.isEmpty()) {
            return PageResult.empty();
        }

        Map<Long, RecruitPost> roleMap = loadRoleMap(applies.stream()
                .map(RecruitApply::getRecruitPostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, CompanyProfile> companyProfileMap = loadCompanyProfileMap(roleMap.values().stream()
                .map(RecruitPost::getCompanyProfileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Set<Long> crewUserIds = roleMap.values().stream()
                .map(RecruitPost::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> actorUserIds = applies.stream()
                .map(RecruitApply::getActorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> userIds = new LinkedHashSet<>(crewUserIds);
        userIds.addAll(actorUserIds);

        Map<Long, User> userMap = loadUserMap(userIds);
        Map<Long, ActorProfile> actorProfileMap = loadActorProfileMap(actorUserIds);
        Map<Long, Map<Long, ProjectRespDTO>> projectMapByCrewUserId = buildProjectMapByCrewUserId(companyProfileMap.values());

        List<AdminRecruitApplyListItemDTO> rows = applies.stream()
                .map(apply -> toApplyItem(
                        apply,
                        roleMap.get(apply.getRecruitPostId()),
                        companyProfileMap,
                        userMap,
                        actorProfileMap,
                        projectMapByCrewUserId))
                .filter(Objects::nonNull)
                .filter(item -> matchesApply(item, query))
                .sorted(Comparator.comparing(AdminRecruitApplyListItemDTO::getApplyTime,
                                Comparator.nullsLast(String::compareTo)).reversed()
                        .thenComparing(AdminRecruitApplyListItemDTO::getApplyId,
                                Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();

        return paginate(rows, query == null ? 1 : query.getPageNo(), query == null ? 20 : query.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProjectStatus(Long projectId, AdminRecruitProjectStatusChangeDTO dto) {
        if (projectId == null) {
            throw new BizException("项目不存在");
        }
        ProjectMutationContext context = requireProjectContext(projectId);
        Integer targetStatus = dto == null ? null : dto.getStatus();
        if (targetStatus == null || (targetStatus != 1 && targetStatus != 2)) {
            throw new BizException("项目状态不合法");
        }
        if (Objects.equals(context.project().getStatus(), targetStatus)) {
            throw new BizException("项目状态已匹配");
        }

        ProjectRespDTO beforeProject = copyProject(context.project());
        List<Long> affectedRoleIds = targetStatus == 2 ? closeProjectRoles(context.profile(), projectId) : Collections.emptyList();

        context.project().setStatus(targetStatus);
        saveCompanyExtras(context.profile(), context.extras());

        ProjectRespDTO afterProject = copyProject(context.project());
        Map<String, Object> extraContext = new LinkedHashMap<>();
        extraContext.put("company_profile_id", context.profile().getCompanyProfileId());
        extraContext.put("crew_user_id", context.profile().getUserId());
        extraContext.put("from_status", beforeProject.getStatus());
        extraContext.put("to_status", afterProject.getStatus());
        extraContext.put("reason", trimToNull(dto == null ? null : dto.getReason()));
        extraContext.put("affected_role_ids", affectedRoleIds);
        extraContext.put("affected_role_count", affectedRoleIds.size());
        if (targetStatus == 1) {
            extraContext.put("note", "仅恢复项目状态，不自动恢复角色招募状态");
        }

        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("recruit")
                .operationCode("project_status_change")
                .targetType("recruit_project")
                .targetId(projectId)
                .beforeSnapshot(snapshotProject(context.profile(), beforeProject))
                .afterSnapshot(snapshotProject(context.profile(), afterProject))
                .extraContext(extraContext)
                .operationResult(1)
                .build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRoleStatus(Long roleId, AdminRecruitRoleStatusChangeDTO dto) {
        RecruitPost post = recruitPostMapper.selectById(roleId);
        if (post == null) {
            throw new BizException("角色不存在");
        }

        Integer targetStatus = requireRecruitPostStatus(dto == null ? null : dto.getStatus());
        if (Objects.equals(post.getPostStatus(), targetStatus)) {
            throw new BizException("角色状态已匹配");
        }

        RoleExtraDTO roleExtra = readRoleExtra(post.getExtendedField());
        CompanyProfile profile = resolveCompanyProfile(post);
        ProjectRespDTO project = findProjectByRole(profile, post.getUserId(), roleExtra);
        if (targetStatus == 1 && project != null && project.getStatus() != null && project.getStatus() != 1) {
            throw new BizException("关联项目已结束，不能恢复招募");
        }

        Map<String, Object> beforeSnapshot = snapshotRole(post, roleExtra, project);
        post.setPostStatus(targetStatus);
        recruitPostMapper.updateById(post);

        Map<String, Object> afterSnapshot = snapshotRole(post, roleExtra, project);
        Map<String, Object> extraContext = new LinkedHashMap<>();
        extraContext.put("crew_user_id", post.getUserId());
        extraContext.put("company_profile_id", post.getCompanyProfileId());
        extraContext.put("project_id", roleExtra.getProjectId());
        extraContext.put("project_status", project == null ? null : project.getStatus());
        extraContext.put("reason", trimToNull(dto == null ? null : dto.getReason()));

        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("recruit")
                .operationCode("role_status_change")
                .targetType("recruit_role")
                .targetId(roleId)
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(afterSnapshot)
                .extraContext(extraContext)
                .operationResult(1)
                .build());
    }

    private AdminRecruitProjectListItemDTO toProjectItem(CompanyProfile profile, User crewUser, ProjectRespDTO project) {
        AdminRecruitProjectListItemDTO item = new AdminRecruitProjectListItemDTO();
        item.setProjectId(project.getId());
        item.setCrewUserId(profile.getUserId());
        item.setCompanyProfileId(profile.getCompanyProfileId());
        item.setCompanyName(firstNonBlank(profile.getCompanyName(), crewUser == null ? null : crewUser.getUserName(), "未命名剧组"));
        item.setContactName(firstNonBlank(profile.getContactName(), null, ""));
        item.setContactPhone(firstNonBlank(profile.getContactPhone(), crewUser == null ? null : crewUser.getPhone(), ""));
        item.setTitle(defaultText(project.getTitle()));
        item.setDescription(defaultText(project.getDescription()));
        item.setLocation(defaultText(project.getLocation()));
        item.setStatus(project.getStatus());
        item.setType(defaultText(project.getType()));
        item.setShootingDate(defaultText(project.getShootingDate()));
        item.setRoleCount(project.getRoleCount());
        item.setCoverImage(firstNonBlank(project.getCoverImage(), profile.getCoverUrl(), profile.getLogoUrl()));
        item.setSourceUpdatedAt(formatDateTime(firstNonNull(profile.getLastUpdate(), profile.getCreateTime())));
        item.setSourceCreatedAt(formatDateTime(profile.getCreateTime()));
        return item;
    }

    private AdminRecruitRoleListItemDTO toRoleItem(RecruitPost post,
                                                   CompanyProfile profile,
                                                   User crewUser,
                                                   Map<Long, ProjectRespDTO> projectMap) {
        RoleExtraDTO roleExtra = readRoleExtra(post.getExtendedField());
        ProjectRespDTO project = roleExtra.getProjectId() == null ? null : projectMap.get(roleExtra.getProjectId());

        AdminRecruitRoleListItemDTO item = new AdminRecruitRoleListItemDTO();
        item.setRoleId(post.getRecruitPostId());
        item.setCrewUserId(post.getUserId());
        item.setCompanyProfileId(post.getCompanyProfileId());
        item.setProjectId(project != null ? project.getId() : roleExtra.getProjectId());
        item.setProjectTitle(firstNonBlank(project == null ? null : project.getTitle(), post.getDramaName(), "未关联项目"));
        item.setCompanyName(firstNonBlank(profile == null ? null : profile.getCompanyName(),
                crewUser == null ? null : crewUser.getUserName(), "未命名剧组"));
        item.setRoleName(firstNonBlank(post.getRoleName(), post.getTitle(), "未命名角色"));
        item.setGender(toGenderText(post.getRequireGender()));
        item.setMinAge(post.getRequireAgeMin());
        item.setMaxAge(post.getRequireAgeMax());
        item.setRequirement(firstNonBlank(post.getRoleDesc(), post.getTitle(), ""));
        item.setFee(resolveSalary(post));
        item.setStatus(resolveRoleStatus(post.getPostStatus()));
        item.setDeadline(formatDate(post.getApplyDeadline()));
        item.setApplyCount(safeInt(post.getApplyCount()));
        item.setLocation(firstNonBlank(project == null ? null : project.getLocation(),
                resolveLocation(post.getShootProvince(), post.getShootCity(), post.getShootAddress()), ""));
        item.setContactName(firstNonBlank(post.getContactName(), profile == null ? null : profile.getContactName(), ""));
        item.setContactPhone(firstNonBlank(post.getContactPhone(),
                profile == null ? null : profile.getContactPhone(),
                crewUser == null ? null : crewUser.getPhone()));
        item.setPublishTime(formatDateTime(firstNonNull(post.getLastUpdate(), post.getCreateTime())));
        item.setCoverImage(firstNonBlank(roleExtra.getCoverImage(),
                project == null ? null : project.getCoverImage(),
                profile == null ? null : profile.getCoverUrl(),
                profile == null ? null : profile.getLogoUrl()));
        item.setTags(mergeTags(roleExtra.getTags(), splitCommaSeparated(post.getRequireStyleTag())));
        return item;
    }

    private AdminRecruitApplyListItemDTO toApplyItem(RecruitApply apply,
                                                     RecruitPost post,
                                                     Map<Long, CompanyProfile> companyProfileMap,
                                                     Map<Long, User> userMap,
                                                     Map<Long, ActorProfile> actorProfileMap,
                                                     Map<Long, Map<Long, ProjectRespDTO>> projectMapByCrewUserId) {
        if (post == null) {
            return null;
        }

        RoleExtraDTO roleExtra = readRoleExtra(post.getExtendedField());
        Map<Long, ProjectRespDTO> projectMap = projectMapByCrewUserId.getOrDefault(post.getUserId(), Collections.emptyMap());
        ProjectRespDTO project = roleExtra.getProjectId() == null ? null : projectMap.get(roleExtra.getProjectId());
        CompanyProfile profile = companyProfileMap.get(post.getCompanyProfileId());
        User crewUser = userMap.get(post.getUserId());
        User actorUser = userMap.get(apply.getActorUserId());
        ActorProfile actorProfile = actorProfileMap.get(apply.getActorUserId());

        AdminRecruitApplyListItemDTO item = new AdminRecruitApplyListItemDTO();
        item.setApplyId(apply.getRecruitApplyId());
        item.setRoleId(apply.getRecruitPostId());
        item.setActorUserId(apply.getActorUserId());
        item.setCrewUserId(post.getUserId());
        item.setProjectId(project != null ? project.getId() : roleExtra.getProjectId());
        item.setProjectTitle(firstNonBlank(project == null ? null : project.getTitle(), post.getDramaName(), "未关联项目"));
        item.setCompanyName(firstNonBlank(profile == null ? null : profile.getCompanyName(),
                crewUser == null ? null : crewUser.getUserName(), "未命名剧组"));
        item.setRoleName(firstNonBlank(post.getRoleName(), post.getTitle(), "未命名角色"));
        item.setRoleStatus(resolveRoleStatus(post.getPostStatus()));
        item.setActorName(firstNonBlank(actorProfile == null ? null : actorProfile.getNickName(),
                actorUser == null ? null : actorUser.getUserName(), "未命名演员"));
        item.setActorPhone(firstNonBlank(actorProfile == null ? null : actorProfile.getPhone(),
                actorUser == null ? null : actorUser.getPhone(), ""));
        item.setActorAvatar(firstNonBlank(actorProfile == null ? null : actorProfile.getAvatarUrl(),
                actorUser == null ? null : actorUser.getAvatarUrl()));
        item.setStatus(toFrontendApplyStatus(apply.getApplyStatus()));
        item.setRemark(defaultText(apply.getApplyMessage()));
        item.setApplyTime(formatDateTime(apply.getCreateTime()));
        return item;
    }

    private boolean matchesProject(AdminRecruitProjectListItemDTO item, AdminRecruitProjectQueryDTO query) {
        if (query == null) {
            return true;
        }
        if (query.getProjectId() != null && !Objects.equals(item.getProjectId(), query.getProjectId())) {
            return false;
        }
        if (query.getCrewUserId() != null && !Objects.equals(item.getCrewUserId(), query.getCrewUserId())) {
            return false;
        }
        if (query.getStatus() != null && !Objects.equals(item.getStatus(), query.getStatus())) {
            return false;
        }
        if (StringUtils.hasText(query.getLocation()) && !containsText(item.getLocation(), query.getLocation())) {
            return false;
        }
        if (StringUtils.hasText(query.getKeyword())) {
            return containsText(item.getTitle(), query.getKeyword())
                    || containsText(item.getDescription(), query.getKeyword())
                    || containsText(item.getCompanyName(), query.getKeyword())
                    || containsText(item.getContactName(), query.getKeyword());
        }
        return true;
    }

    private boolean matchesRole(AdminRecruitRoleListItemDTO item, AdminRecruitRoleQueryDTO query) {
        if (query == null) {
            return true;
        }
        if (query.getRoleId() != null && !Objects.equals(item.getRoleId(), query.getRoleId())) {
            return false;
        }
        if (query.getCrewUserId() != null && !Objects.equals(item.getCrewUserId(), query.getCrewUserId())) {
            return false;
        }
        if (query.getProjectId() != null && !Objects.equals(item.getProjectId(), query.getProjectId())) {
            return false;
        }
        if (StringUtils.hasText(query.getStatus()) && !Objects.equals(item.getStatus(), query.getStatus().trim())) {
            return false;
        }
        if (StringUtils.hasText(query.getKeyword())) {
            return containsText(item.getRoleName(), query.getKeyword())
                    || containsText(item.getProjectTitle(), query.getKeyword())
                    || containsText(item.getCompanyName(), query.getKeyword())
                    || containsText(item.getRequirement(), query.getKeyword());
        }
        return true;
    }

    private boolean matchesApply(AdminRecruitApplyListItemDTO item, AdminRecruitApplyQueryDTO query) {
        if (query == null) {
            return true;
        }
        if (query.getApplyId() != null && !Objects.equals(item.getApplyId(), query.getApplyId())) {
            return false;
        }
        if (query.getRoleId() != null && !Objects.equals(item.getRoleId(), query.getRoleId())) {
            return false;
        }
        if (query.getActorUserId() != null && !Objects.equals(item.getActorUserId(), query.getActorUserId())) {
            return false;
        }
        if (query.getCrewUserId() != null && !Objects.equals(item.getCrewUserId(), query.getCrewUserId())) {
            return false;
        }
        if (query.getStatus() != null && !Objects.equals(item.getStatus(), query.getStatus())) {
            return false;
        }
        if (StringUtils.hasText(query.getKeyword())) {
            return containsText(item.getActorName(), query.getKeyword())
                    || containsText(item.getRoleName(), query.getKeyword())
                    || containsText(item.getProjectTitle(), query.getKeyword())
                    || containsText(item.getCompanyName(), query.getKeyword());
        }
        return true;
    }

    private Map<Long, Map<Long, ProjectRespDTO>> buildProjectMapByCrewUserId(Collection<CompanyProfile> profiles) {
        Map<Long, Map<Long, ProjectRespDTO>> result = new LinkedHashMap<>();
        for (CompanyProfile profile : profiles) {
            if (profile == null || profile.getUserId() == null) {
                continue;
            }
            Map<Long, ProjectRespDTO> projectMap = safeProjects(readCompanyExtras(profile.getExtendedField())).stream()
                    .filter(project -> project.getId() != null)
                    .collect(Collectors.toMap(ProjectRespDTO::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
            result.put(profile.getUserId(), projectMap);
        }
        return result;
    }

    private Map<Long, CompanyProfile> loadCompanyProfileMap(Set<Long> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return companyProfileMapper.selectBatchIds(profileIds).stream()
                .collect(Collectors.toMap(CompanyProfile::getCompanyProfileId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, User> loadUserMap(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, ActorProfile> loadActorProfileMap(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return actorProfileMapper.selectList(new LambdaQueryWrapper<ActorProfile>()
                        .in(ActorProfile::getUserId, userIds)
                        .orderByDesc(ActorProfile::getLastUpdate)
                        .orderByDesc(ActorProfile::getActorProfileId))
                .stream()
                .collect(Collectors.toMap(ActorProfile::getUserId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, RecruitPost> loadRoleMap(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return recruitPostMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(RecruitPost::getRecruitPostId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private ProjectMutationContext requireProjectContext(Long projectId) {
        List<CompanyProfile> profiles = companyProfileMapper.selectList(new LambdaQueryWrapper<CompanyProfile>()
                .orderByDesc(CompanyProfile::getLastUpdate)
                .orderByDesc(CompanyProfile::getCompanyProfileId));
        for (CompanyProfile profile : profiles) {
            CompanyProfileExtrasDTO extras = readCompanyExtras(profile.getExtendedField());
            for (ProjectRespDTO project : safeProjectRefs(extras)) {
                if (Objects.equals(project.getId(), projectId)) {
                    return new ProjectMutationContext(profile, extras, project);
                }
            }
        }
        throw new BizException("项目不存在");
    }

    private CompanyProfileExtrasDTO readCompanyExtras(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new CompanyProfileExtrasDTO();
        }
        try {
            CompanyProfileExtrasDTO extras = objectMapper.readValue(raw, CompanyProfileExtrasDTO.class);
            if (extras.getProjects() == null) {
                extras.setProjects(new ArrayList<>());
            }
            return extras;
        } catch (Exception ignored) {
            return new CompanyProfileExtrasDTO();
        }
    }

    private void saveCompanyExtras(CompanyProfile profile, CompanyProfileExtrasDTO extras) {
        profile.setExtendedField(writeCompanyExtras(extras));
        companyProfileMapper.updateById(profile);
    }

    private String writeCompanyExtras(CompanyProfileExtrasDTO extras) {
        try {
            return objectMapper.writeValueAsString(extras == null ? new CompanyProfileExtrasDTO() : extras);
        } catch (Exception e) {
            throw new BizException("项目数据序列化失败");
        }
    }

    private RoleExtraDTO readRoleExtra(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new RoleExtraDTO();
        }
        try {
            RoleExtraDTO extra = objectMapper.readValue(raw, RoleExtraDTO.class);
            if (extra.getTags() == null) {
                extra.setTags(new ArrayList<>());
            }
            return extra;
        } catch (Exception ignored) {
            return new RoleExtraDTO();
        }
    }

    private List<ProjectRespDTO> safeProjects(CompanyProfileExtrasDTO extras) {
        if (extras == null || extras.getProjects() == null) {
            return Collections.emptyList();
        }
        return safeProjectRefs(extras).stream()
                .map(this::copyProject)
                .toList();
    }

    private List<ProjectRespDTO> safeProjectRefs(CompanyProfileExtrasDTO extras) {
        if (extras == null || extras.getProjects() == null) {
            return Collections.emptyList();
        }
        return extras.getProjects().stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private ProjectRespDTO copyProject(ProjectRespDTO source) {
        ProjectRespDTO target = new ProjectRespDTO();
        if (source == null) {
            return target;
        }
        target.setId(source.getId());
        target.setCompanyId(source.getCompanyId());
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

    private List<Long> closeProjectRoles(CompanyProfile profile, Long projectId) {
        if (profile == null || profile.getUserId() == null || projectId == null) {
            return Collections.emptyList();
        }
        List<RecruitPost> posts = recruitPostMapper.selectList(new LambdaQueryWrapper<RecruitPost>()
                .eq(RecruitPost::getUserId, profile.getUserId())
                .orderByDesc(RecruitPost::getRecruitPostId));
        List<Long> affectedRoleIds = new ArrayList<>();
        for (RecruitPost post : posts) {
            RoleExtraDTO extra = readRoleExtra(post.getExtendedField());
            if (!Objects.equals(extra.getProjectId(), projectId)) {
                continue;
            }
            if (Objects.equals(post.getPostStatus(), 2)) {
                continue;
            }
            post.setPostStatus(2);
            recruitPostMapper.updateById(post);
            affectedRoleIds.add(post.getRecruitPostId());
        }
        return affectedRoleIds;
    }

    private ProjectRespDTO findProjectByRole(CompanyProfile profile, Long crewUserId, RoleExtraDTO roleExtra) {
        if (roleExtra == null || roleExtra.getProjectId() == null) {
            return null;
        }
        if (profile != null) {
            ProjectRespDTO project = safeProjects(readCompanyExtras(profile.getExtendedField())).stream()
                    .filter(item -> Objects.equals(item.getId(), roleExtra.getProjectId()))
                    .findFirst()
                    .orElse(null);
            if (project != null) {
                return project;
            }
        }
        if (crewUserId == null) {
            return null;
        }
        return companyProfileMapper.selectList(new LambdaQueryWrapper<CompanyProfile>()
                        .eq(CompanyProfile::getUserId, crewUserId)
                        .orderByDesc(CompanyProfile::getLastUpdate)
                        .orderByDesc(CompanyProfile::getCompanyProfileId))
                .stream()
                .map(CompanyProfile::getExtendedField)
                .map(this::readCompanyExtras)
                .flatMap(extras -> safeProjects(extras).stream())
                .filter(project -> Objects.equals(project.getId(), roleExtra.getProjectId()))
                .findFirst()
                .orElse(null);
    }

    private CompanyProfile resolveCompanyProfile(RecruitPost post) {
        if (post == null) {
            return null;
        }
        if (post.getCompanyProfileId() != null) {
            CompanyProfile profile = companyProfileMapper.selectById(post.getCompanyProfileId());
            if (profile != null) {
                return profile;
            }
        }
        if (post.getUserId() == null) {
            return null;
        }
        return companyProfileMapper.selectOne(new LambdaQueryWrapper<CompanyProfile>()
                .eq(CompanyProfile::getUserId, post.getUserId())
                .orderByDesc(CompanyProfile::getLastUpdate)
                .orderByDesc(CompanyProfile::getCompanyProfileId)
                .last("limit 1"));
    }

    private Integer toRecruitPostStatus(String roleStatus) {
        if (!StringUtils.hasText(roleStatus)) {
            return null;
        }
        return switch (roleStatus.trim()) {
            case "recruiting" -> 1;
            case "closed" -> 2;
            case "paused" -> 3;
            default -> null;
        };
    }

    private Integer requireRecruitPostStatus(String roleStatus) {
        Integer status = toRecruitPostStatus(roleStatus);
        if (status == null) {
            throw new BizException("角色状态不合法");
        }
        return status;
    }

    private void applyStatusFilter(LambdaQueryWrapper<RecruitApply> wrapper, Integer frontendStatus) {
        if (frontendStatus == null) {
            return;
        }
        if (frontendStatus == 1) {
            wrapper.in(RecruitApply::getApplyStatus, 1, 2);
            return;
        }
        if (frontendStatus == 2) {
            wrapper.in(RecruitApply::getApplyStatus, 3, 4);
            return;
        }
        if (frontendStatus == 3) {
            wrapper.eq(RecruitApply::getApplyStatus, 5);
            return;
        }
        if (frontendStatus == 4) {
            wrapper.eq(RecruitApply::getApplyStatus, 6);
        }
    }

    private int toFrontendApplyStatus(Integer applyStatus) {
        if (applyStatus != null && (applyStatus == 3 || applyStatus == 4)) {
            return 2;
        }
        if (applyStatus != null && applyStatus == 5) {
            return 3;
        }
        if (applyStatus != null && applyStatus == 6) {
            return 4;
        }
        return 1;
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

    private Map<String, Object> snapshotProject(CompanyProfile profile, ProjectRespDTO project) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("projectId", project == null ? null : project.getId());
        snapshot.put("companyProfileId", profile == null ? null : profile.getCompanyProfileId());
        snapshot.put("crewUserId", profile == null ? null : profile.getUserId());
        snapshot.put("companyName", profile == null ? null : profile.getCompanyName());
        snapshot.put("title", project == null ? null : project.getTitle());
        snapshot.put("status", project == null ? null : project.getStatus());
        snapshot.put("location", project == null ? null : project.getLocation());
        snapshot.put("shootingDate", project == null ? null : project.getShootingDate());
        snapshot.put("roleCount", project == null ? null : project.getRoleCount());
        return snapshot;
    }

    private Map<String, Object> snapshotRole(RecruitPost post, RoleExtraDTO roleExtra, ProjectRespDTO project) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("roleId", post.getRecruitPostId());
        snapshot.put("crewUserId", post.getUserId());
        snapshot.put("companyProfileId", post.getCompanyProfileId());
        snapshot.put("projectId", roleExtra == null ? null : roleExtra.getProjectId());
        snapshot.put("projectStatus", project == null ? null : project.getStatus());
        snapshot.put("projectTitle", project == null ? null : project.getTitle());
        snapshot.put("roleName", post.getRoleName());
        snapshot.put("postStatus", post.getPostStatus());
        snapshot.put("status", resolveRoleStatus(post.getPostStatus()));
        snapshot.put("applyDeadline", formatDate(post.getApplyDeadline()));
        snapshot.put("applyCount", post.getApplyCount());
        return snapshot;
    }

    private String resolveSalary(RecruitPost post) {
        if (!StringUtils.hasText(post.getSalary()) || Boolean.FALSE.equals(post.getSalaryVisible())) {
            return "面议";
        }
        return post.getSalary().trim();
    }

    private String resolveLocation(String province, String city, String address) {
        if (StringUtils.hasText(city)) {
            return city.trim();
        }
        if (StringUtils.hasText(province)) {
            return province.trim();
        }
        return StringUtils.hasText(address) ? address.trim() : "";
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

    private List<String> mergeTags(List<String> extraTags, List<String> styleTags) {
        Set<String> values = new LinkedHashSet<>();
        if (extraTags != null) {
            values.addAll(extraTags.stream().filter(StringUtils::hasText).map(String::trim).toList());
        }
        if (styleTags != null) {
            values.addAll(styleTags.stream().filter(StringUtils::hasText).map(String::trim).toList());
        }
        return new ArrayList<>(values);
    }

    private List<String> splitCommaSeparated(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private boolean containsText(String source, String keyword) {
        return StringUtils.hasText(source) && StringUtils.hasText(keyword)
                && source.toLowerCase().contains(keyword.trim().toLowerCase());
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_FORMATTER);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMATTER);
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }

    private int safeInt(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private <T> PageResult<T> paginate(List<T> source, int pageNo, int pageSize) {
        if (source == null || source.isEmpty()) {
            return PageResult.empty();
        }
        int safePageNo = pageNo <= 0 ? 1 : pageNo;
        int safePageSize = pageSize <= 0 ? 20 : pageSize;
        int start = Math.min((safePageNo - 1) * safePageSize, source.size());
        int end = Math.min(start + safePageSize, source.size());
        return new PageResult<>(source.size(), new ArrayList<>(source.subList(start, end)));
    }

    private LocalDateTime firstNonNull(LocalDateTime first, LocalDateTime second) {
        return first != null ? first : second;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record ProjectMutationContext(CompanyProfile profile, CompanyProfileExtrasDTO extras, ProjectRespDTO project) {
    }
}
