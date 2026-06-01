package com.kaipai.service.referral;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.referral.dto.UserEntitlementGrantDetailDTO;
import com.kaipai.model.referral.dto.UserEntitlementGrantExtendRequestDTO;
import com.kaipai.model.referral.dto.UserEntitlementGrantGrantRequestDTO;
import com.kaipai.model.referral.dto.UserEntitlementGrantItemDTO;
import com.kaipai.model.referral.dto.UserEntitlementGrantListQueryDTO;
import com.kaipai.model.referral.dto.UserEntitlementGrantRevokeRequestDTO;
import com.kaipai.model.referral.entity.UserEntitlementGrant;

public interface UserEntitlementGrantService extends IService<UserEntitlementGrant> {

    PageResult<UserEntitlementGrantItemDTO> adminGrantList(UserEntitlementGrantListQueryDTO query);

    UserEntitlementGrantItemDTO adminGrantItem(Long grantId);

    UserEntitlementGrantDetailDTO adminGrantDetail(Long grantId);

    UserEntitlementGrant grantManual(UserEntitlementGrantGrantRequestDTO request);

    void revokeManual(UserEntitlementGrantRevokeRequestDTO request);

    void extendGrant(UserEntitlementGrantExtendRequestDTO request);
}
