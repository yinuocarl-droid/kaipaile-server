package com.kaipai.module.server.refund.service;

import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.refund.dto.RefundOperateLogItemDTO;
import com.kaipai.module.model.refund.dto.RefundOperateLogQueryDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.module.model.refund.entity.RefundOperateLog;

import java.util.List;

public interface RefundOperateLogService extends IService<RefundOperateLog> {

    List<RefundOperateLogItemDTO> listByRefundOrderId(Long refundOrderId);

    PageResult<RefundOperateLogItemDTO> adminLogList(RefundOperateLogQueryDTO query);
}
