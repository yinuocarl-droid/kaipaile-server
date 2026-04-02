package com.kaipai.module.server.recruit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.recruit.dto.RecruitRoleQueryDTO;
import com.kaipai.module.model.recruit.dto.RecruitRoleRespDTO;
import com.kaipai.module.model.recruit.entity.RecruitPost;

public interface RecruitPostService extends IService<RecruitPost> {

    PageResult<RecruitRoleRespDTO> searchRoles(RecruitRoleQueryDTO query);

    RecruitRoleRespDTO detail(Long roleId);
}
