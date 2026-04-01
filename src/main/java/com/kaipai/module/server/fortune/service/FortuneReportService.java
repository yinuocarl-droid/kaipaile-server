package com.kaipai.module.server.fortune.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.module.model.fortune.dto.FortuneReportRespDTO;
import com.kaipai.module.model.fortune.entity.FortuneReport;

public interface FortuneReportService extends IService<FortuneReport> {

    FortuneReportRespDTO currentReport(Long userId);

    void applyLuckyColor(Long userId, String sceneKey);
}
