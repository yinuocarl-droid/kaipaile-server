package com.kaipai.module.server.capability.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.capability.dto.CapabilityChangeLogItemDTO;
import com.kaipai.module.model.capability.dto.CapabilityChangeLogQueryDTO;
import com.kaipai.module.model.capability.entity.CapabilityChangeLog;

public interface CapabilityChangeLogService extends IService<CapabilityChangeLog> {

    PageResult<CapabilityChangeLogItemDTO> adminLogList(CapabilityChangeLogQueryDTO query);
}
