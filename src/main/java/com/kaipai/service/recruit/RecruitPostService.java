package com.kaipai.service.recruit;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.recruit.dto.RecruitRoleQueryDTO;
import com.kaipai.model.recruit.dto.RecruitRoleRespDTO;
import com.kaipai.model.recruit.entity.RecruitPost;

public interface RecruitPostService extends IService<RecruitPost> {

    PageResult<RecruitRoleRespDTO> searchRoles(RecruitRoleQueryDTO query);

    RecruitRoleRespDTO detail(Long roleId);
}
