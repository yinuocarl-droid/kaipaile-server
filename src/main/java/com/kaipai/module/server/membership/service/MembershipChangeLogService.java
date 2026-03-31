package com.kaipai.module.server.membership.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.membership.dto.MembershipChangeLogItemDTO;
import com.kaipai.module.model.membership.dto.MembershipChangeLogQueryDTO;
import com.kaipai.module.model.membership.entity.MembershipChangeLog;

public interface MembershipChangeLogService extends IService<MembershipChangeLog> {

    PageResult<MembershipChangeLogItemDTO> adminLogList(MembershipChangeLogQueryDTO query);
}
