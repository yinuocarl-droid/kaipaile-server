package com.kaipai.module.company.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.company.entity.CompanyProfile;
import com.kaipai.module.company.mapper.CompanyProfileMapper;
import com.kaipai.module.company.service.CompanyProfileService;
import org.springframework.stereotype.Service;

@Service
public class CompanyProfileServiceImpl extends ServiceImpl<CompanyProfileMapper, CompanyProfile> implements CompanyProfileService {
}
