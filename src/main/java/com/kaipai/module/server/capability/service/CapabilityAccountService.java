package com.kaipai.module.server.capability.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.level.dto.ActorLevelInfoRespDTO;
import com.kaipai.module.model.capability.dto.AdminCapabilityAccountDetailDTO;
import com.kaipai.module.model.capability.dto.AdminCapabilityAccountItemDTO;
import com.kaipai.module.model.capability.dto.CapabilityAccountCloseDTO;
import com.kaipai.module.model.capability.dto.CapabilityAccountExtendDTO;
import com.kaipai.module.model.capability.dto.CapabilityAccountOpenDTO;
import com.kaipai.module.model.capability.dto.CapabilityAccountQueryDTO;
import com.kaipai.module.model.capability.entity.CapabilityAccount;

public interface CapabilityAccountService extends IService<CapabilityAccount> {

    ActorLevelInfoRespDTO actorLevelInfo(Long userId);

    PageResult<AdminCapabilityAccountItemDTO> adminAccountList(CapabilityAccountQueryDTO query);

    AdminCapabilityAccountDetailDTO adminAccountDetail(Long userId);

    void openAccount(Long userId, CapabilityAccountOpenDTO dto);

    void extendAccount(Long userId, CapabilityAccountExtendDTO dto);

    void closeAccount(Long userId, CapabilityAccountCloseDTO dto);
}
