package com.kaipai.module.server.recruit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.recruit.entity.RecruitApply;
import com.kaipai.module.server.recruit.mapper.RecruitApplyMapper;
import com.kaipai.module.server.recruit.service.RecruitApplyService;
import org.springframework.stereotype.Service;

@Service
public class RecruitApplyServiceImpl extends ServiceImpl<RecruitApplyMapper, RecruitApply> implements RecruitApplyService {
}
