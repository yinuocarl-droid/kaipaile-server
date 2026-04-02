package com.kaipai.module.server.recruit.service;

import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.recruit.dto.ApplyCreateDTO;
import com.kaipai.module.model.recruit.dto.ApplyQueryDTO;
import com.kaipai.module.model.recruit.dto.ApplyRespDTO;
import com.kaipai.module.model.recruit.dto.ProjectQueryDTO;
import com.kaipai.module.model.recruit.dto.ProjectRespDTO;
import com.kaipai.module.model.recruit.dto.ProjectSaveDTO;
import com.kaipai.module.model.recruit.dto.RoleQueryDTO;
import com.kaipai.module.model.recruit.dto.RoleRespDTO;
import com.kaipai.module.model.recruit.dto.RoleSaveDTO;

public interface MiniProgramRecruitService {

    ProjectRespDTO createProject(Long currentUserId, ProjectSaveDTO dto);

    void updateProject(Long currentUserId, Long projectId, ProjectSaveDTO dto);

    void deleteProject(Long currentUserId, Long projectId);

    ProjectRespDTO project(Long currentUserId, Long projectId);

    PageResult<ProjectRespDTO> myProjects(Long currentUserId, ProjectQueryDTO query);

    PageResult<ProjectRespDTO> projectList(ProjectQueryDTO query);

    RoleRespDTO createRole(Long currentUserId, RoleSaveDTO dto);

    void updateRole(Long currentUserId, Long roleId, RoleSaveDTO dto);

    void deleteRole(Long currentUserId, Long roleId);

    RoleRespDTO role(Long roleId);

    PageResult<RoleRespDTO> rolesByProject(Long currentUserId, Long projectId, RoleQueryDTO query);

    PageResult<RoleRespDTO> searchRoles(RoleQueryDTO query);

    ApplyRespDTO submitApply(Long currentUserId, ApplyCreateDTO dto);

    void cancelApply(Long currentUserId, Long applyId);

    PageResult<ApplyRespDTO> myApplies(Long currentUserId, ApplyQueryDTO query);

    PageResult<ApplyRespDTO> appliesByRole(Long currentUserId, Long roleId, ApplyQueryDTO query);

    void approveApply(Long currentUserId, Long applyId, String remark);

    void rejectApply(Long currentUserId, Long applyId, String remark);

    ApplyRespDTO applyDetail(Long currentUserId, Long applyId);
}
