package com.kaipai.service.verify;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.verify.dto.IdentityVerificationAuditReqDTO;
import com.kaipai.model.verify.dto.IdentityVerificationDetailRespDTO;
import com.kaipai.model.verify.dto.IdentityVerificationListItemDTO;
import com.kaipai.model.verify.dto.IdentityVerificationListReqDTO;
import com.kaipai.model.verify.dto.IdentityVerificationStatusRespDTO;
import com.kaipai.model.verify.dto.IdentityVerificationSubmitReqDTO;
import com.kaipai.model.verify.entity.IdentityVerification;

public interface IdentityVerificationService extends IService<IdentityVerification> {

    IdentityVerificationStatusRespDTO currentStatus(Long userId);

    IdentityVerificationStatusRespDTO submit(Long userId, IdentityVerificationSubmitReqDTO req);

    PageResult<IdentityVerificationListItemDTO> adminList(IdentityVerificationListReqDTO req);

    IdentityVerificationDetailRespDTO adminDetail(Long id);

    void approve(Long id, IdentityVerificationAuditReqDTO req);

    void reject(Long id, IdentityVerificationAuditReqDTO req);
}
