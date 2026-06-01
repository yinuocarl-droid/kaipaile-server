package com.kaipai.service.recruit;

import com.kaipai.common.result.PageResult;
import com.kaipai.model.recruit.dto.AdminRecruitApplyListItemDTO;
import com.kaipai.model.recruit.dto.AdminRecruitApplyQueryDTO;
import com.kaipai.model.recruit.dto.AdminRecruitProjectListItemDTO;
import com.kaipai.model.recruit.dto.AdminRecruitProjectQueryDTO;
import com.kaipai.model.recruit.dto.AdminRecruitProjectStatusChangeDTO;
import com.kaipai.model.recruit.dto.AdminRecruitRoleListItemDTO;
import com.kaipai.model.recruit.dto.AdminRecruitRoleQueryDTO;
import com.kaipai.model.recruit.dto.AdminRecruitRoleStatusChangeDTO;

public interface AdminRecruitGovernanceService {

    PageResult<AdminRecruitProjectListItemDTO> projectList(AdminRecruitProjectQueryDTO query);

    PageResult<AdminRecruitRoleListItemDTO> roleList(AdminRecruitRoleQueryDTO query);

    PageResult<AdminRecruitApplyListItemDTO> applyList(AdminRecruitApplyQueryDTO query);

    void updateProjectStatus(Long projectId, AdminRecruitProjectStatusChangeDTO dto);

    void updateRoleStatus(Long roleId, AdminRecruitRoleStatusChangeDTO dto);
}
