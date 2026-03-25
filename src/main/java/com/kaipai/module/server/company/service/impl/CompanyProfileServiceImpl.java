package com.kaipai.module.server.company.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.company.entity.CompanyProfile;
import com.kaipai.module.server.company.mapper.CompanyProfileMapper;
import com.kaipai.module.server.company.service.CompanyProfileService;
import org.springframework.stereotype.Service;

@Service
public class CompanyProfileServiceImpl extends ServiceImpl<CompanyProfileMapper, CompanyProfile> implements CompanyProfileService {
}
