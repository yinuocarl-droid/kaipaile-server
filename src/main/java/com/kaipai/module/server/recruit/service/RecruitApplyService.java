package com.kaipai.module.server.recruit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.recruit.dto.RecruitApplyQueryDTO;
import com.kaipai.module.model.recruit.dto.RecruitApplyRespDTO;
import com.kaipai.module.model.recruit.entity.RecruitApply;

public interface RecruitApplyService extends IService<RecruitApply> {

    RecruitApplyRespDTO submit(Long actorUserId, Long roleId, String remark);

    void cancel(Long actorUserId, Long applyId);

    PageResult<RecruitApplyRespDTO> myApplies(Long actorUserId, RecruitApplyQueryDTO query);

    PageResult<RecruitApplyRespDTO> roleApplies(Long crewUserId, Long roleId, RecruitApplyQueryDTO query);

    void approve(Long crewUserId, Long applyId);

    void reject(Long crewUserId, Long applyId, String remark);

    RecruitApplyRespDTO detail(Long currentUserId, Long applyId);
}
