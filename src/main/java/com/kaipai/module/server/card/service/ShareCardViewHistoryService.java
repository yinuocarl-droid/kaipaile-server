package com.kaipai.module.server.card.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.module.model.card.dto.ShareCardHistoryItemDTO;
import com.kaipai.module.model.card.dto.ShareCardHistoryRecordDTO;
import com.kaipai.module.model.card.entity.ShareCardViewHistory;

import java.util.List;

public interface ShareCardViewHistoryService extends IService<ShareCardViewHistory> {

    void record(Long viewerUserId, ShareCardHistoryRecordDTO dto);

    List<ShareCardHistoryItemDTO> myHistory(Long viewerUserId);

    void clear(Long viewerUserId);
}



