package com.kaipai.module.server.fortune.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.module.model.fortune.entity.FortuneReport;
import com.kaipai.module.server.fortune.mapper.FortuneReportMapper;
import com.kaipai.module.server.fortune.service.FortuneReportService;
import org.springframework.stereotype.Service;

@Service
public class FortuneReportServiceImpl extends ServiceImpl<FortuneReportMapper, FortuneReport> implements FortuneReportService {
}
