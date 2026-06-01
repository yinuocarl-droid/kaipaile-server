package com.kaipai.service.refund;

import com.kaipai.common.result.PageResult;
import com.kaipai.model.refund.dto.RefundOperateLogItemDTO;
import com.kaipai.model.refund.dto.RefundOperateLogQueryDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.model.refund.entity.RefundOperateLog;

import java.util.List;

public interface RefundOperateLogService extends IService<RefundOperateLog> {

    List<RefundOperateLogItemDTO> listByRefundOrderId(Long refundOrderId);

    PageResult<RefundOperateLogItemDTO> adminLogList(RefundOperateLogQueryDTO query);
}
