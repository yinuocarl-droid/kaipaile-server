package com.kaipai.module.server.membership.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.membership.dto.MembershipAccountCloseDTO;
import com.kaipai.module.model.membership.dto.MembershipAccountExtendDTO;
import com.kaipai.module.model.membership.dto.MembershipAccountOpenDTO;
import com.kaipai.module.model.membership.dto.MembershipAccountQueryDTO;
import com.kaipai.module.model.membership.entity.MembershipAccount;

public interface MembershipAccountService extends IService<MembershipAccount> {

    PageResult<MembershipAccount> adminAccountList(MembershipAccountQueryDTO query);

    void openAccount(Long userId, MembershipAccountOpenDTO dto);

    void extendAccount(Long userId, MembershipAccountExtendDTO dto);

    void closeAccount(Long userId, MembershipAccountCloseDTO dto);
}
