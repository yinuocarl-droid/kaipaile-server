package com.kaipai.service.crew;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.model.crew.dto.CrewProfileRespDTO;
import com.kaipai.model.crew.dto.CrewProfileSaveDTO;
import com.kaipai.model.crew.entity.CrewProfile;

public interface CrewProfileService extends IService<CrewProfile> {

    CrewProfileRespDTO mineProfile(Long currentUserId);

    CrewProfileRespDTO profile(Long userId);

    void saveProfile(Long currentUserId, CrewProfileSaveDTO dto);
}
