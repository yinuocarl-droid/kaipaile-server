package com.kaipai.module.server.crew.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.module.model.crew.dto.CrewProfileRespDTO;
import com.kaipai.module.model.crew.dto.CrewProfileSaveDTO;
import com.kaipai.module.model.crew.entity.CrewProfile;

public interface CrewProfileService extends IService<CrewProfile> {

    CrewProfileRespDTO mineProfile(Long currentUserId);

    CrewProfileRespDTO profile(Long userId);

    void saveProfile(Long currentUserId, CrewProfileSaveDTO dto);
}
