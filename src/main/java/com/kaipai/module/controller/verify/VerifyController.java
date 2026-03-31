package com.kaipai.module.controller.verify;

import com.kaipai.module.server.verify.service.IdentityVerificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "实名认证")
@RestController
@RequestMapping("/verify")
@RequiredArgsConstructor
public class VerifyController {

    private final IdentityVerificationService identityVerificationService;
}
