package com.kaipai.module.server.recruit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.company.dto.CompanyProfileExtrasDTO;
import com.kaipai.module.model.company.dto.CompanyProfileRespDTO;
import com.kaipai.module.model.company.entity.CompanyProfile;
import com.kaipai.module.model.recruit.dto.ApplyCreateDTO;
import com.kaipai.module.model.recruit.dto.ApplyQueryDTO;
import com.kaipai.module.model.recruit.dto.ApplyRespDTO;
import com.kaipai.module.model.recruit.dto.ProjectQueryDTO;
import com.kaipai.module.model.recruit.dto.ProjectRespDTO;
import com.kaipai.module.model.recruit.dto.ProjectSaveDTO;
import com.kaipai.module.model.recruit.dto.RecruitApplyQueryDTO;
import com.kaipai.module.model.recruit.dto.RecruitApplyRespDTO;
import com.kaipai.module.model.recruit.dto.RecruitRoleQueryDTO;
import com.kaipai.module.model.recruit.dto.RecruitRoleRespDTO;
import com.kaipai.module.model.recruit.dto.RoleExtraDTO;
import com.kaipai.module.model.recruit.dto.RoleQueryDTO;
import com.kaipai.module.model.recruit.dto.RoleRespDTO;
import com.kaipai.module.model.recruit.dto.RoleSaveDTO;
import com.kaipai.module.model.recruit.entity.RecruitPost;
import com.kaipai.module.model.user.entity.User;
import com.kaipai.module.server.company.mapper.CompanyProfileMapper;
import com.kaipai.module.server.company.service.CompanyProfileService;
import com.kaipai.module.server.recruit.mapper.RecruitPostMapper;
import com.kaipai.module.server.recruit.service.MiniProgramRecruitService;
import com.kaipai.module.server.recruit.service.RecruitApplyService;
import com.kaipai.module.server.recruit.service.RecruitPostService;
import com.kaipai.module.server.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MiniProgramRecruitServiceImpl implements MiniProgramRecruitService {

    private static final int USER_TYPE_ACTOR = 1;
    private static final int USER_TYPE_CREW = 2;
    private static final int PROJECT_STATUS_ACTIVE = 1;

    private final CompanyProfileMapper companyProfileMapper;
    private final CompanyProfileService companyProfileService;
    private final RecruitPostMapper recruitPostMapper;
    private final RecruitPostService recruitPostService;
    private final RecruitApplyService recruitApplyService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectRespDTO createProject(Long currentUserId, ProjectSaveDTO dto) {
        requireCrewUser(currentUserId);
        CompanyProfile profile = ensureCompanyProfile(currentUserId);
        CompanyProfileExtrasDTO extras = readCompanyExtras(profile.getExtendedField());
        List<ProjectRespDTO> projects = new ArrayList<>(safeProjects(extras.getProjects()));

        ProjectRespDTO project = new ProjectRespDTO();
        project.setId(nextId());
        project.setCompanyId(currentUserId);
        project.setTitle(requireText(dto == null ? null : dto.getTitle(), "项目名称不能为空"));
        project.setDescription(defaultText(dto == null ? null : dto.getDescription()));
        project.setLocation(defaultText(dto == null ? null : dto.getLocation()));
        project.setStatus(dto == null || dto.getStatus() == null ? PROJECT_STATUS_ACTIVE : dto.getStatus());
        project.setType(defaultText(dto == null ? null : dto.getType()));
        project.setShootingDate(defaultText(dto == null ? null : dto.getShootingDate()));
        project.setRoleCount(0);
        project.setCoverImage(defaultText(dto == null ? null : dto.getCoverImage()));

        projects.add(0, project);
        extras.setProjects(projects);
        saveCompanyExtras(profile, extras);
        return enrichProject(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProject(Long currentUserId, Long projectId, ProjectSaveDTO dto) {
        requireCrewUser(currentUserId);
        CompanyProfile profile = ensureCompanyProfile(currentUserId);
        CompanyProfileExtrasDTO extras = readCompanyExtras(profile.getExtendedField());
        ProjectRespDTO project = requireMutableProject(extras, projectId);

        if (dto != null) {
            if (StringUtils.hasText(dto.getTitle())) {
                project.setTitle(dto.getTitle().trim());
            }
            if (dto.getDescription() != null) {
                project.setDescription(dto.getDescription().trim());
            }
            if (dto.getLocation() != null) {
                project.setLocation(dto.getLocation().trim());
            }
            if (dto.getStatus() != null) {
                project.setStatus(dto.getStatus());
            }
            if (dto.getType() != null) {
                project.setType(dto.getType().trim());
            }
            if (dto.getShootingDate() != null) {
                project.setShootingDate(dto.getShootingDate().trim());
            }
            if (dto.getCoverImage() != null) {
                project.setCoverImage(dto.getCoverImage().trim());
            }
        }

        saveCompanyExtras(profile, extras);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProject(Long currentUserId, Long projectId) {
        requireCrewUser(currentUserId);
        CompanyProfile profile = ensureCompanyProfile(currentUserId);
        CompanyProfileExtrasDTO extras = readCompanyExtras(profile.getExtendedField());
        List<ProjectRespDTO> projects = new ArrayList<>(safeProjects(extras.getProjects()));
        boolean removed = projects.removeIf(item -> Objects.equals(item.getId(), projectId));
        if (!removed) {
            throw new BizException("项目不存在");
        }
        extras.setProjects(projects);
        saveCompanyExtras(profile, extras);

        List<RecruitPost> posts = recruitPostMapper.selectList(new LambdaQueryWrapper<RecruitPost>()
                .eq(RecruitPost::getUserId, currentUserId)
                .orderByDesc(RecruitPost::getRecruitPostId));
        List<Long> roleIds = posts.stream()
                .filter(post -> Objects.equals(readRoleExtra(post.getExtendedField()).getProjectId(), projectId))
                .map(RecruitPost::getRecruitPostId)
                .toList();
        if (!roleIds.isEmpty()) {
            recruitPostMapper.deleteBatchIds(roleIds);
        }
    }

    @Override
    public ProjectRespDTO project(Long currentUserId, Long projectId) {
        requireCrewUser(currentUserId);
        CompanyProfileExtrasDTO extras = readCompanyExtras(ensureCompanyProfile(currentUserId).getExtendedField());
        return enrichProject(requireProject(extras, projectId));
    }

    @Override
    public PageResult<ProjectRespDTO> myProjects(Long currentUserId, ProjectQueryDTO query) {
        requireCrewUser(currentUserId);
        CompanyProfileExtrasDTO extras = readCompanyExtras(ensureCompanyProfile(currentUserId).getExtendedField());
        return paginateProjects(filterProjects(safeProjects(extras.getProjects()), currentUserId, query), query);
    }

    @Override
    public PageResult<ProjectRespDTO> projectList(ProjectQueryDTO query) {
        List<ProjectRespDTO> projects = companyProfileMapper.selectList(new LambdaQueryWrapper<CompanyProfile>()
                        .orderByDesc(CompanyProfile::getCompanyProfileId))
                .stream()
                .flatMap(profile -> safeProjects(readCompanyExtras(profile.getExtendedField()).getProjects()).stream())
                .map(this::enrichProject)
                .toList();
        return paginateProjects(filterProjects(projects, null, query), query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleRespDTO createRole(Long currentUserId, RoleSaveDTO dto) {
        requireCrewUser(currentUserId);
        CompanyProfile profile = ensureCompanyProfile(currentUserId);
        CompanyProfileExtrasDTO extras = readCompanyExtras(profile.getExtendedField());
        ProjectRespDTO project = requireProject(extras, dto == null ? null : dto.getProjectId());

        RecruitPost post = new RecruitPost();
        post.setUserId(currentUserId);
        post.setCompanyProfileId(profile.getCompanyProfileId());
        post.setPostNo("RP" + nextId());
        post.setTitle(firstNonBlank(dto == null ? null : dto.getRoleName(), project.getTitle(), "角色招募"));
        post.setDramaName(project.getTitle());
        post.setRoleName(requireText(dto == null ? null : dto.getRoleName(), "角色名称不能为空"));
        post.setRoleDesc(defaultText(dto == null ? null : dto.getRequirement()));
        post.setRequireGender(toGenderCode(dto == null ? null : dto.getGender()));
        post.setRequireAgeMin(dto == null ? null : dto.getMinAge());
        post.setRequireAgeMax(dto == null ? null : dto.getMaxAge());
        post.setShootCity(trimToNull(project.getLocation()));
        post.setShootStartTime(parseDate(project.getShootingDate(), false));
        post.setShootEndTime(parseDate(project.getShootingDate(), true));
        post.setSalary(trimToNull(dto == null ? null : dto.getFee()));
        post.setSalaryVisible(true);
        post.setContactName(trimToNull(profile.getContactName()));
        post.setContactPhone(trimToNull(profile.getContactPhone()));
        post.setApplyDeadline(parseDate(dto == null ? null : dto.getDeadline(), true));
        post.setPostType(1);
        post.setPostStatus(1);
        post.setViewCount(0);
        post.setApplyCount(0);

        RoleExtraDTO roleExtra = new RoleExtraDTO();
        roleExtra.setProjectId(project.getId());
        roleExtra.setCoverImage(trimToNull(dto == null ? null : dto.getCoverImage()));
        post.setExtendedField(writeRoleExtra(roleExtra));

        recruitPostMapper.insert(post);
        return buildManagedRole(post.getRecruitPostId(), project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long currentUserId, Long roleId, RoleSaveDTO dto) {
        requireCrewUser(currentUserId);
        RecruitPost post = requireOwnedRole(currentUserId, roleId);
        CompanyProfile profile = ensureCompanyProfile(currentUserId);
        CompanyProfileExtrasDTO extras = readCompanyExtras(profile.getExtendedField());
        RoleExtraDTO roleExtra = readRoleExtra(post.getExtendedField());
        ProjectRespDTO project = roleExtra.getProjectId() == null ? null : findProject(extras, roleExtra.getProjectId());
        if (dto != null && dto.getProjectId() != null && !Objects.equals(dto.getProjectId(), roleExtra.getProjectId())) {
            project = requireProject(extras, dto.getProjectId());
            roleExtra.setProjectId(project.getId());
        }

        if (dto != null) {
            if (StringUtils.hasText(dto.getRoleName())) {
                post.setTitle(dto.getRoleName().trim());
                post.setRoleName(dto.getRoleName().trim());
            }
            if (dto.getRequirement() != null) {
                post.setRoleDesc(dto.getRequirement().trim());
            }
            if (dto.getGender() != null) {
                post.setRequireGender(toGenderCode(dto.getGender()));
            }
            if (dto.getMinAge() != null) {
                post.setRequireAgeMin(dto.getMinAge());
            }
            if (dto.getMaxAge() != null) {
                post.setRequireAgeMax(dto.getMaxAge());
            }
            if (dto.getFee() != null) {
                post.setSalary(dto.getFee().trim());
            }
            if (dto.getDeadline() != null) {
                post.setApplyDeadline(parseDate(dto.getDeadline(), true));
            }
            if (dto.getCoverImage() != null) {
                roleExtra.setCoverImage(dto.getCoverImage().trim());
            }
        }

        if (project != null) {
            post.setDramaName(project.getTitle());
            post.setShootCity(trimToNull(project.getLocation()));
            post.setShootStartTime(parseDate(project.getShootingDate(), false));
            post.setShootEndTime(parseDate(project.getShootingDate(), true));
        }
        post.setExtendedField(writeRoleExtra(roleExtra));
        recruitPostMapper.updateById(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long currentUserId, Long roleId) {
        requireCrewUser(currentUserId);
        requireOwnedRole(currentUserId, roleId);
        recruitPostMapper.deleteById(roleId);
    }

    @Override
    public RoleRespDTO role(Long roleId) {
        RecruitRoleRespDTO role = recruitPostService.detail(roleId);
        RoleRespDTO result = toRoleResp(role);
        RoleExtraDTO extra = readRoleExtra(recruitPostMapper.selectById(roleId).getExtendedField());
        if (extra.getProjectId() != null && result.getCompany() != null) {
            CompanyProfile profile = loadCompanyProfileByUserId(result.getCompany().getUserId());
            ProjectRespDTO project = profile == null ? null : findProject(readCompanyExtras(profile.getExtendedField()), extra.getProjectId());
            if (project != null) {
                result.setProjectId(project.getId());
                result.setProject(enrichProject(project));
            }
        }
        return result;
    }

    @Override
    public PageResult<RoleRespDTO> rolesByProject(Long currentUserId, Long projectId, RoleQueryDTO query) {
        requireCrewUser(currentUserId);
        CompanyProfileExtrasDTO extras = readCompanyExtras(ensureCompanyProfile(currentUserId).getExtendedField());
        ProjectRespDTO project = requireProject(extras, projectId);

        List<RecruitPost> posts = recruitPostMapper.selectList(new LambdaQueryWrapper<RecruitPost>()
                .eq(RecruitPost::getUserId, currentUserId)
                .orderByDesc(RecruitPost::getCreateTime)
                .orderByDesc(RecruitPost::getRecruitPostId));

        List<RoleRespDTO> list = posts.stream()
                .filter(post -> Objects.equals(readRoleExtra(post.getExtendedField()).getProjectId(), projectId))
                .map(post -> buildManagedRole(post.getRecruitPostId(), project))
                .toList();
        return paginateRoles(list, query);
    }

    @Override
    public PageResult<RoleRespDTO> searchRoles(RoleQueryDTO query) {
        RecruitRoleQueryDTO recruitQuery = new RecruitRoleQueryDTO();
        recruitQuery.setPage(query == null ? 1 : query.getPage());
        recruitQuery.setSize(query == null ? 20 : query.getSize());
        recruitQuery.setGender(query == null ? null : query.getGender());
        recruitQuery.setMinAge(query == null ? null : query.getMinAge());
        recruitQuery.setMaxAge(query == null ? null : query.getMaxAge());
        recruitQuery.setKeyword(query == null ? null : query.getKeyword());
        PageResult<RecruitRoleRespDTO> result = recruitPostService.searchRoles(recruitQuery);
        return new PageResult<>(result.getTotal(), result.getList().stream().map(this::toRoleResp).toList());
    }

    @Override
    public ApplyRespDTO submitApply(Long currentUserId, ApplyCreateDTO dto) {
        return toApplyResp(recruitApplyService.submit(currentUserId, dto == null ? null : dto.getRoleId(), dto == null ? null : dto.getRemark()));
    }

    @Override
    public void cancelApply(Long currentUserId, Long applyId) {
        recruitApplyService.cancel(currentUserId, applyId);
    }

    @Override
    public PageResult<ApplyRespDTO> myApplies(Long currentUserId, ApplyQueryDTO query) {
        RecruitApplyQueryDTO recruitQuery = toApplyQuery(query);
        PageResult<RecruitApplyRespDTO> result = recruitApplyService.myApplies(currentUserId, recruitQuery);
        return new PageResult<>(result.getTotal(), result.getList().stream().map(this::toApplyResp).toList());
    }

    @Override
    public PageResult<ApplyRespDTO> appliesByRole(Long currentUserId, Long roleId, ApplyQueryDTO query) {
        RecruitApplyQueryDTO recruitQuery = toApplyQuery(query);
        PageResult<RecruitApplyRespDTO> result = recruitApplyService.roleApplies(currentUserId, roleId, recruitQuery);
        return new PageResult<>(result.getTotal(), result.getList().stream().map(this::toApplyResp).toList());
    }

    @Override
    public void approveApply(Long currentUserId, Long applyId, String remark) {
        recruitApplyService.approve(currentUserId, applyId);
    }

    @Override
    public void rejectApply(Long currentUserId, Long applyId, String remark) {
        recruitApplyService.reject(currentUserId, applyId, remark);
    }

    @Override
    public ApplyRespDTO applyDetail(Long currentUserId, Long applyId) {
        return toApplyResp(recruitApplyService.detail(currentUserId, applyId));
    }

    private ProjectRespDTO enrichProject(ProjectRespDTO project) {
        ProjectRespDTO copy = copyProject(project);
        copy.setRoleCount(countRolesByProject(copy.getCompanyId(), copy.getId()));
        return copy;
    }

    private List<ProjectRespDTO> filterProjects(List<ProjectRespDTO> projects, Long currentUserId, ProjectQueryDTO query) {
        return projects.stream()
                .filter(item -> currentUserId == null || Objects.equals(item.getCompanyId(), currentUserId))
                .filter(item -> query == null || query.getStatus() == null || Objects.equals(item.getStatus(), query.getStatus()))
                .filter(item -> !StringUtils.hasText(query == null ? null : query.getLocation()) || containsText(item.getLocation(), query.getLocation()))
                .filter(item -> !StringUtils.hasText(query == null ? null : query.getKeyword())
                        || containsText(item.getTitle(), query.getKeyword())
                        || containsText(item.getDescription(), query.getKeyword()))
                .sorted(Comparator.comparing(ProjectRespDTO::getId, Comparator.nullsLast(Long::compareTo)).reversed())
                .map(this::enrichProject)
                .toList();
    }

    private PageResult<ProjectRespDTO> paginateProjects(List<ProjectRespDTO> projects, ProjectQueryDTO query) {
        int pageNo = safePageNo(query == null ? null : query.getPage());
        int pageSize = safePageSize(query == null ? null : query.getSize());
        int start = Math.min((pageNo - 1) * pageSize, projects.size());
        int end = Math.min(start + pageSize, projects.size());
        return new PageResult<>(projects.size(), new ArrayList<>(projects.subList(start, end)));
    }

    private PageResult<RoleRespDTO> paginateRoles(List<RoleRespDTO> roles, RoleQueryDTO query) {
        int pageNo = safePageNo(query == null ? null : query.getPage());
        int pageSize = safePageSize(query == null ? null : query.getSize());
        int start = Math.min((pageNo - 1) * pageSize, roles.size());
        int end = Math.min(start + pageSize, roles.size());
        return new PageResult<>(roles.size(), new ArrayList<>(roles.subList(start, end)));
    }

    private RoleRespDTO buildManagedRole(Long roleId, ProjectRespDTO project) {
        RoleRespDTO role = toRoleResp(recruitPostService.detail(roleId));
        role.setProjectId(project.getId());
        role.setProject(enrichProject(project));
        CompanyProfileRespDTO company = companyProfileService.profile(project.getCompanyId());
        role.setCompany(company);
        return role;
    }

    private ProjectRespDTO requireProject(CompanyProfileExtrasDTO extras, Long projectId) {
        ProjectRespDTO project = findProject(extras, projectId);
        if (project == null) {
            throw new BizException("项目不存在");
        }
        return project;
    }

    private ProjectRespDTO findProject(CompanyProfileExtrasDTO extras, Long projectId) {
        ProjectRespDTO project = findMutableProject(extras, projectId);
        return project == null ? null : copyProject(project);
    }

    private ProjectRespDTO requireMutableProject(CompanyProfileExtrasDTO extras, Long projectId) {
        ProjectRespDTO project = findMutableProject(extras, projectId);
        if (project == null) {
            throw new BizException("项目不存在");
        }
        return project;
    }

    private ProjectRespDTO findMutableProject(CompanyProfileExtrasDTO extras, Long projectId) {
        if (projectId == null) {
            return null;
        }
        return safeProjectRefs(extras).stream()
                .filter(item -> Objects.equals(item.getId(), projectId))
                .findFirst()
                .orElse(null);
    }

    private CompanyProfile ensureCompanyProfile(Long userId) {
        CompanyProfile profile = loadCompanyProfileByUserId(userId);
        if (profile != null) {
            return profile;
        }
        CompanyProfile created = new CompanyProfile();
        created.setUserId(userId);
        created.setCompanyStatus(1);
        created.setExtendedField(writeCompanyExtras(new CompanyProfileExtrasDTO()));
        companyProfileMapper.insert(created);
        return created;
    }

    private CompanyProfile loadCompanyProfileByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        return companyProfileMapper.selectOne(new LambdaQueryWrapper<CompanyProfile>()
                .eq(CompanyProfile::getUserId, userId)
                .last("limit 1"));
    }

    private CompanyProfileExtrasDTO readCompanyExtras(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new CompanyProfileExtrasDTO();
        }
        try {
            return objectMapper.readValue(raw, CompanyProfileExtrasDTO.class);
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
            return objectMapper.readValue(raw, RoleExtraDTO.class);
        } catch (Exception ignored) {
            return new RoleExtraDTO();
        }
    }

    private String writeRoleExtra(RoleExtraDTO extra) {
        try {
            return objectMapper.writeValueAsString(extra == null ? new RoleExtraDTO() : extra);
        } catch (Exception e) {
            throw new BizException("角色数据序列化失败");
        }
    }

    private RecruitPost requireOwnedRole(Long currentUserId, Long roleId) {
        RecruitPost post = recruitPostMapper.selectById(roleId);
        if (post == null) {
            throw new BizException("角色不存在");
        }
        if (!Objects.equals(post.getUserId(), currentUserId)) {
            throw new BizException("只能操作自己的角色");
        }
        return post;
    }

    private int countRolesByProject(Long companyId, Long projectId) {
        if (companyId == null || projectId == null) {
            return 0;
        }
        return (int) recruitPostMapper.selectList(new LambdaQueryWrapper<RecruitPost>()
                        .eq(RecruitPost::getUserId, companyId))
                .stream()
                .filter(post -> Objects.equals(readRoleExtra(post.getExtendedField()).getProjectId(), projectId))
                .count();
    }

    private RoleRespDTO toRoleResp(RecruitRoleRespDTO source) {
        RoleRespDTO target = new RoleRespDTO();
        target.setId(source.getId());
        target.setProjectId(source.getProjectId());
        target.setRoleName(source.getRoleName());
        target.setGender(source.getGender());
        target.setMinAge(source.getMinAge());
        target.setMaxAge(source.getMaxAge());
        target.setRequirement(source.getRequirement());
        target.setFee(source.getFee());
        target.setDeadline(source.getDeadline());
        target.setStatus(source.getStatus());
        target.setTags(source.getTags() == null ? new ArrayList<>() : new ArrayList<>(source.getTags()));
        target.setPublishTime(source.getPublishTime());
        target.setCoverImage(source.getCoverImage());
        target.setProject(toProjectResp(source.getProject()));
        target.setCompany(toCompanyResp(source.getCompany()));
        return target;
    }

    private ProjectRespDTO toProjectResp(RecruitRoleRespDTO.ProjectDTO source) {
        if (source == null) {
            return null;
        }
        ProjectRespDTO target = new ProjectRespDTO();
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

    private CompanyProfileRespDTO toCompanyResp(RecruitRoleRespDTO.CompanyDTO source) {
        if (source == null) {
            return null;
        }
        CompanyProfileRespDTO target = new CompanyProfileRespDTO();
        target.setUserId(source.getUserId());
        target.setAvatar(source.getAvatar());
        target.setCompanyName(source.getCompanyName());
        target.setContactName(source.getContactName());
        target.setContactPhone(source.getContactPhone());
        target.setRemark(source.getRemark());
        target.setLocation(source.getLocation());
        target.setCompanyType(source.getCompanyType());
        target.setTeamScale(source.getTeamScale());
        target.setFocusDirection(source.getFocusDirection());
        target.setRepresentativeWorks(source.getRepresentativeWorks());
        target.setCooperationNeed(source.getCooperationNeed());
        target.setOfficeAddress(source.getOfficeAddress());
        return target;
    }

    private ApplyRespDTO toApplyResp(RecruitApplyRespDTO source) {
        ApplyRespDTO target = new ApplyRespDTO();
        target.setId(source.getId());
        target.setRoleId(source.getRoleId());
        target.setActorId(source.getActorId());
        target.setStatus(source.getStatus());
        target.setRemark(source.getRemark());
        target.setApplyTime(source.getApplyTime());
        target.setActorName(source.getActorName());
        target.setActorAvatar(source.getActorAvatar());
        target.setActorPhone(source.getActorPhone());
        target.setRoleName(source.getRoleName());
        target.setProjectName(source.getProjectName());
        target.setRole(source.getRole() == null ? null : toRoleResp(source.getRole()));
        return target;
    }

    private RecruitApplyQueryDTO toApplyQuery(ApplyQueryDTO query) {
        RecruitApplyQueryDTO recruitQuery = new RecruitApplyQueryDTO();
        recruitQuery.setPage(query == null ? 1 : query.getPage());
        recruitQuery.setSize(query == null ? 20 : query.getSize());
        recruitQuery.setStatus(query == null ? null : query.getStatus());
        recruitQuery.setRoleId(query == null ? null : query.getRoleId());
        return recruitQuery;
    }

    private ProjectRespDTO copyProject(ProjectRespDTO source) {
        ProjectRespDTO target = new ProjectRespDTO();
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

    private List<ProjectRespDTO> safeProjects(List<ProjectRespDTO> projects) {
        return projects == null ? Collections.emptyList() : projects.stream().map(this::copyProject).collect(Collectors.toList());
    }

    private List<ProjectRespDTO> safeProjectRefs(CompanyProfileExtrasDTO extras) {
        if (extras == null || extras.getProjects() == null) {
            return Collections.emptyList();
        }
        return extras.getProjects().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void requireCrewUser(Long userId) {
        User user = requireUser(userId);
        if (!Objects.equals(user.getUserType(), USER_TYPE_CREW)) {
            throw new BizException("只有剧组账号可以执行该操作");
        }
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private boolean containsText(String source, String keyword) {
        return StringUtils.hasText(source) && StringUtils.hasText(keyword) && source.contains(keyword.trim());
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return fallback;
    }

    private Integer toGenderCode(String gender) {
        if (!StringUtils.hasText(gender) || "不限".equals(gender.trim())) {
            return 0;
        }
        if ("男".equals(gender.trim()) || "male".equalsIgnoreCase(gender.trim())) {
            return 1;
        }
        if ("女".equals(gender.trim()) || "female".equalsIgnoreCase(gender.trim())) {
            return 2;
        }
        return 0;
    }

    private LocalDateTime parseDate(String raw, boolean endOfDay) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            String normalized = raw.contains(" - ") ? raw.substring(endOfDay ? raw.lastIndexOf(" - ") + 3 : 0, endOfDay ? raw.length() : raw.indexOf(" - ")) : raw;
            LocalDate date = LocalDate.parse(normalized.trim());
            return date.atTime(endOfDay ? LocalTime.of(23, 59, 59) : LocalTime.MIN);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int safePageNo(Integer page) {
        return page == null || page <= 0 ? 1 : page;
    }

    private int safePageSize(Integer size) {
        return size == null || size <= 0 ? 20 : size;
    }

    private long nextId() {
        return System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
    }
}
