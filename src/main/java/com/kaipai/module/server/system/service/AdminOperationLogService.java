package com.kaipai.module.server.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.system.dto.AdminOperationLogDetailRespDTO;
import com.kaipai.module.model.system.dto.AdminOperationLogListItemDTO;
import com.kaipai.module.model.system.dto.AdminOperationLogQueryDTO;
import com.kaipai.module.model.system.entity.AdminOperationLog;

public interface AdminOperationLogService extends IService<AdminOperationLog> {

    PageResult<AdminOperationLogListItemDTO> adminOperationLogList(AdminOperationLogQueryDTO query);

    AdminOperationLogDetailRespDTO adminOperationLogDetail(Long operationLogId);
}
