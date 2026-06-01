package com.kaipai.service.card;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.model.card.dto.ShareCardHistoryItemDTO;
import com.kaipai.model.card.dto.ShareCardHistoryRecordDTO;
import com.kaipai.model.card.entity.ShareCardViewHistory;

import java.util.List;

public interface ShareCardViewHistoryService extends IService<ShareCardViewHistory> {

    void record(Long viewerUserId, ShareCardHistoryRecordDTO dto);

    List<ShareCardHistoryItemDTO> myHistory(Long viewerUserId);

    void clear(Long viewerUserId);
}



