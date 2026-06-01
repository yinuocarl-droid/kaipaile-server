package com.kaipai.service.capability;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.level.dto.ActorLevelInfoRespDTO;
import com.kaipai.model.capability.dto.AdminCapabilityAccountDetailDTO;
import com.kaipai.model.capability.dto.AdminCapabilityAccountItemDTO;
import com.kaipai.model.capability.dto.CapabilityAccountCloseDTO;
import com.kaipai.model.capability.dto.CapabilityAccountExtendDTO;
import com.kaipai.model.capability.dto.CapabilityAccountOpenDTO;
import com.kaipai.model.capability.dto.CapabilityAccountQueryDTO;
import com.kaipai.model.capability.entity.CapabilityAccount;

public interface CapabilityAccountService extends IService<CapabilityAccount> {

    ActorLevelInfoRespDTO actorLevelInfo(Long userId);

    PageResult<AdminCapabilityAccountItemDTO> adminAccountList(CapabilityAccountQueryDTO query);

    AdminCapabilityAccountDetailDTO adminAccountDetail(Long userId);

    void openAccount(Long userId, CapabilityAccountOpenDTO dto);

    void extendAccount(Long userId, CapabilityAccountExtendDTO dto);

    void closeAccount(Long userId, CapabilityAccountCloseDTO dto);
}
