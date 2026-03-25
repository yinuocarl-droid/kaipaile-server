package com.kaipai.module.controller.invite;

import com.kaipai.module.server.invite.service.InviteRecordService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "邀约记录管理")
@RestController
@RequestMapping("/invite")
@RequiredArgsConstructor
public class InviteRecordController {

    private final InviteRecordService inviteRecordService;
}
