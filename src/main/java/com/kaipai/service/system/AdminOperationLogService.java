package com.kaipai.service.system;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.system.dto.AdminOperationLogDetailRespDTO;
import com.kaipai.model.system.dto.AdminOperationLogListItemDTO;
import com.kaipai.model.system.dto.AdminOperationLogQueryDTO;
import com.kaipai.model.system.entity.AdminOperationLog;

public interface AdminOperationLogService extends IService<AdminOperationLog> {

    PageResult<AdminOperationLogListItemDTO> adminOperationLogList(AdminOperationLogQueryDTO query);

    AdminOperationLogDetailRespDTO adminOperationLogDetail(Long operationLogId);
}


