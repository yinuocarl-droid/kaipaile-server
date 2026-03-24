package com.kaipai.module.recruit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.recruit.entity.RecruitPost;
import com.kaipai.module.recruit.mapper.RecruitPostMapper;
import com.kaipai.module.recruit.service.RecruitPostService;
import org.springframework.stereotype.Service;

@Service
public class RecruitPostServiceImpl extends ServiceImpl<RecruitPostMapper, RecruitPost> implements RecruitPostService {
}
