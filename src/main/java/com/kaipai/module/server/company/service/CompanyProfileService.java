package com.kaipai.module.server.company.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.module.model.company.dto.CompanyProfileRespDTO;
import com.kaipai.module.model.company.dto.CompanyProfileSaveDTO;
import com.kaipai.module.model.company.entity.CompanyProfile;

public interface CompanyProfileService extends IService<CompanyProfile> {

    CompanyProfileRespDTO mineProfile(Long currentUserId);

    CompanyProfileRespDTO profile(Long userId);

    void saveProfile(Long currentUserId, CompanyProfileSaveDTO dto);
}
