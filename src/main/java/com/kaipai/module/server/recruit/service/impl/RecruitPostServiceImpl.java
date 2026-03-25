package com.kaipai.module.server.recruit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.recruit.entity.RecruitPost;
import com.kaipai.module.server.recruit.mapper.RecruitPostMapper;
import com.kaipai.module.server.recruit.service.RecruitPostService;
import org.springframework.stereotype.Service;

@Service
public class RecruitPostServiceImpl extends ServiceImpl<RecruitPostMapper, RecruitPost> implements RecruitPostService {
}
