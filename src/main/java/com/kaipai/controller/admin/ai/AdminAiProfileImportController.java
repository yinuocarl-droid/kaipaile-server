package com.kaipai.controller.admin.ai;

import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.result.R;
import com.kaipai.model.ai.dto.ProfileImportConfigAuditRespDTO;
import com.kaipai.model.ai.dto.ProfileImportConfigRespDTO;
import com.kaipai.model.ai.dto.ProfileImportPublicConfigUpdateDTO;
import com.kaipai.model.ai.dto.ProfileImportSecretUpdateDTO;
import com.kaipai.service.ai.ProfileImportConfigService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ai/profile-import")
@RequiredArgsConstructor
public class AdminAiProfileImportController {
    private final ProfileImportConfigService service;
    private final AdminAuthContext authContext;

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('page.system.ai-profile-import')")
    public R<ProfileImportConfigRespDTO> config() {
        return R.ok(service.adminConfig());
    }

    @GetMapping("/audits")
    @PreAuthorize("hasAuthority('action.system.ai-profile-import.audit')")
    public R<List<ProfileImportConfigAuditRespDTO>> audits() {
        return R.ok(service.audits());
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('action.system.ai-profile-import.update')")
    public R<ProfileImportConfigRespDTO> save(
            @RequestBody @Valid ProfileImportPublicConfigUpdateDTO dto) {
        return R.ok(service.savePublicConfig(currentAdminId(), dto));
    }

    @PutMapping("/secret")
    @PreAuthorize("hasAuthority('action.system.ai-profile-import.secret')")
    public R<ProfileImportConfigRespDTO> secret(@RequestBody ProfileImportSecretUpdateDTO dto) {
        return R.ok(service.saveSecret(currentAdminId(), dto));
    }

    @PutMapping("/enabled")
    @PreAuthorize("hasAuthority('action.system.ai-profile-import.update')")
    public R<ProfileImportConfigRespDTO> enabled(@RequestParam boolean value) {
        return R.ok(service.setEnabled(currentAdminId(), value));
    }

    @PostMapping("/test")
    @PreAuthorize("hasAuthority('action.system.ai-profile-import.test')")
    public R<ProfileImportConfigRespDTO> test() {
        return R.ok(service.testConnection(currentAdminId()));
    }

    private Long currentAdminId() {
        return authContext.requireCurrentAdmin().getAdminUserId();
    }
}
