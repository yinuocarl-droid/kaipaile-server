package com.kaipai.controller.api.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kaipai.model.ai.dto.ProfileImportCapabilityRespDTO;
import com.kaipai.service.ai.ProfileImportConfigService;
import com.kaipai.service.ai.ProfileImportService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class AiProfileImportControllerTest {
    @Test
    void exposesGovernedCapabilityToAuthenticatedMiniProgramUser() {
        ProfileImportService importService = mock(ProfileImportService.class);
        ProfileImportConfigService configService = mock(ProfileImportConfigService.class);
        when(configService.capability()).thenReturn(new ProfileImportCapabilityRespDTO(true, null));
        AiProfileImportController controller = new AiProfileImportController(importService, configService);

        var response = controller.capability(new UsernamePasswordAuthenticationToken(7L, null));

        assertTrue(response.getData().isAvailable());
        verify(configService).capability();
    }
}
