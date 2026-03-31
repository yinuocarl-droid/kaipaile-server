package com.kaipai.module.server.verify.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.verify.dto.IdentityVerificationAuditReqDTO;
import com.kaipai.module.model.verify.dto.IdentityVerificationDetailRespDTO;
import com.kaipai.module.model.verify.dto.IdentityVerificationListItemDTO;
import com.kaipai.module.model.verify.dto.IdentityVerificationListReqDTO;
import com.kaipai.module.model.verify.entity.IdentityVerification;

public interface IdentityVerificationService extends IService<IdentityVerification> {

    PageResult<IdentityVerificationListItemDTO> adminList(IdentityVerificationListReqDTO req);

    IdentityVerificationDetailRespDTO adminDetail(Long id);

    void approve(Long id, IdentityVerificationAuditReqDTO req);

    void reject(Long id, IdentityVerificationAuditReqDTO req);
}
