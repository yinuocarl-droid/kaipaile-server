package com.kaipai.service.card;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.card.dto.AdminContactRequestDetailDTO;
import com.kaipai.model.card.dto.AdminContactRequestItemDTO;
import com.kaipai.model.card.dto.AdminContactRequestQueryDTO;
import com.kaipai.model.card.dto.ContactRequestApplyDTO;
import com.kaipai.model.card.dto.ContactRequestDecisionDTO;
import com.kaipai.model.card.dto.ContactRequestItemDTO;
import com.kaipai.model.card.dto.ContactRequestStatusRespDTO;
import com.kaipai.model.card.entity.ShareCardContactRequest;

import java.util.List;

public interface ShareCardContactRequestService extends IService<ShareCardContactRequest> {

    PageResult<AdminContactRequestItemDTO> adminContactRequestList(AdminContactRequestQueryDTO query);

    AdminContactRequestDetailDTO adminContactRequestDetail(Long requestId);

    ContactRequestStatusRespDTO adminApprove(Long requestId, ContactRequestDecisionDTO dto);

    ContactRequestStatusRespDTO adminReject(Long requestId, ContactRequestDecisionDTO dto);

    ContactRequestStatusRespDTO apply(Long viewerUserId, ContactRequestApplyDTO dto);

    ContactRequestStatusRespDTO status(Long viewerUserId, Long shareCardId);

    List<ContactRequestItemDTO> approvedContacts(Long viewerUserId);

    List<ContactRequestItemDTO> ownedRequests(Long holderUserId, String status);

    ContactRequestStatusRespDTO approve(Long holderUserId, Long requestId, ContactRequestDecisionDTO dto);

    ContactRequestStatusRespDTO reject(Long holderUserId, Long requestId, ContactRequestDecisionDTO dto);
}



