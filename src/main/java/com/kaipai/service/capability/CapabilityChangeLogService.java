package com.kaipai.service.capability;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.capability.dto.CapabilityChangeLogItemDTO;
import com.kaipai.model.capability.dto.CapabilityChangeLogQueryDTO;
import com.kaipai.model.capability.entity.CapabilityChangeLog;

public interface CapabilityChangeLogService extends IService<CapabilityChangeLog> {

    PageResult<CapabilityChangeLogItemDTO> adminLogList(CapabilityChangeLogQueryDTO query);
}
