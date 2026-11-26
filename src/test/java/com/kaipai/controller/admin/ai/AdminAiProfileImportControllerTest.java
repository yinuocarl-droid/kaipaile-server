package com.kaipai.controller.admin.ai;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kaipai.common.auth.AdminAuthContext;
import com.kaipai.common.auth.AdminAuthenticatedUser;
import com.kaipai.model.ai.dto.ProfileImportConfigRespDTO;
import com.kaipai.model.ai.dto.ProfileImportPublicConfigUpdateDTO;
import com.kaipai.model.ai.dto.ProfileImportSecretUpdateDTO;
import com.kaipai.service.ai.ProfileImportConfigService;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdminAiProfileImportControllerTest {
    @Test
    void mutationAndTestEndpointsUseAuthenticatedAdminId() {
        ProfileImportConfigService service = mock(ProfileImportConfigService.class);
        AdminAuthContext authContext = mock(AdminAuthContext.class);
        AdminAuthenticatedUser admin = AdminAuthenticatedUser.builder()
                .adminUserId(73L)
                .account("ops")
                .userName("配置管理员")
                .roleCodes(Set.of("system-admin"))
                .permissions(Set.of())
                .build();
        when(authContext.requireCurrentAdmin()).thenReturn(admin);
        ProfileImportConfigRespDTO response = new ProfileImportConfigRespDTO();
        ProfileImportPublicConfigUpdateDTO publicConfig = new ProfileImportPublicConfigUpdateDTO();
        ProfileImportSecretUpdateDTO secret = new ProfileImportSecretUpdateDTO("sk-private-value");
        when(service.savePublicConfig(73L, publicConfig)).thenReturn(response);
        when(service.saveSecret(73L, secret)).thenReturn(response);
        when(service.setEnabled(73L, true)).thenReturn(response);
        when(service.testConnection(73L)).thenReturn(response);
        AdminAiProfileImportController controller =
                new AdminAiProfileImportController(service, authContext);

        controller.save(publicConfig);
        controller.secret(secret);
        controller.enabled(true);
        controller.test();

        verify(service).savePublicConfig(73L, publicConfig);
        verify(service).saveSecret(73L, secret);
        verify(service).setEnabled(73L, true);
        verify(service).testConnection(73L);
    }
}
