package com.kaipai.service.capability.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.model.capability.dto.AdminCapabilityBenefitOverviewDTO;
import com.kaipai.model.capability.dto.CapabilityBenefitQueryDTO;
import com.kaipai.model.capability.dto.CapabilityBenefitSaveDTO;
import com.kaipai.model.capability.entity.CapabilityProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CapabilityProductServiceImplTest {

    @Mock
    private AdminOperationLogger adminOperationLogger;

    private CapabilityProductServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new CapabilityProductServiceImpl(adminOperationLogger, new ObjectMapper()));
    }

    @Test
    void adminBenefitOverviewShouldBuildBenefitItemsAndBenefitCapabilityItems() {
        CapabilityProduct product = new CapabilityProduct();
        product.setProductId(10L);
        product.setProductCode("pro-year");
        product.setProductName("高级 Annual");
        product.setCapabilityTier(2);
        product.setStatus(1);
        product.setLastUpdate(LocalDateTime.of(2026, 3, 31, 12, 0));
        product.setBenefitConfigJson("""
                {
                  "benefitItems": [
                    {
                      "benefitCode": "invite_priority",
                      "benefitName": "Invite Priority",
                      "capabilitySummary": "优先发放邀请资格",
                      "status": 1,
                      "affectedPages": ["referral", "user-center"],
                      "artifactTypes": ["poster"]
                    }
                  ]
                }
                """);
        doReturn(List.of(product)).when(service).list(any(LambdaQueryWrapper.class));

        AdminCapabilityBenefitOverviewDTO overview = service.adminBenefitOverview(new CapabilityBenefitQueryDTO());

        assertEquals(1, overview.getBenefitItems().size());
        AdminCapabilityBenefitOverviewDTO.BenefitItem item = overview.getBenefitItems().get(0);
        assertEquals("10:invite_priority", item.getBenefitId());
        assertEquals("invite_priority", item.getBenefitCode());
        assertEquals(List.of("referral", "user-center"), item.getAffectedPages());

        assertEquals(1, overview.getBenefitCapabilityItems().size());
        AdminCapabilityBenefitOverviewDTO.BenefitCapabilityItem capabilityItem = overview.getBenefitCapabilityItems().get(0);
        assertEquals("invite_priority", capabilityItem.getBenefitCode());
        assertEquals(Boolean.TRUE, capabilityItem.getTierEnabledMap().get(2));
        assertEquals(List.of("pro-year"), capabilityItem.getRelatedProductCodes());
    }

    @Test
    void createBenefitShouldAppendBenefitAndLogOperation() {
        CapabilityProduct product = new CapabilityProduct();
        product.setProductId(11L);
        product.setProductCode("plus-quarter");
        product.setProductName("Plus Quarter");
        product.setCapabilityTier(1);
        product.setStatus(1);
        product.setBenefitConfigJson("{\"benefitItems\":[]}");

        CapabilityBenefitSaveDTO dto = new CapabilityBenefitSaveDTO();
        dto.setProductId(11L);
        dto.setBenefitCode("resume_boost");
        dto.setBenefitName("Resume Boost");
        dto.setCapabilitySummary("提升简历曝光");
        dto.setStatus(1);
        dto.setAffectedPages(List.of("profile"));
        dto.setArtifactTypes(List.of("miniProgramCard"));

        doReturn(product).when(service).getById(11L);
        doReturn(true).when(service).updateById(any(CapabilityProduct.class));

        service.createBenefit(dto);

        ArgumentCaptor<CapabilityProduct> productCaptor = ArgumentCaptor.forClass(CapabilityProduct.class);
        verify(service).updateById(productCaptor.capture());
        assertTrue(productCaptor.getValue().getBenefitConfigJson().contains("\"benefitCode\":\"resume_boost\""));

        ArgumentCaptor<AdminOperationLogCommand> logCaptor = ArgumentCaptor.forClass(AdminOperationLogCommand.class);
        verify(adminOperationLogger).log(logCaptor.capture());
        assertEquals("benefit_create", logCaptor.getValue().getOperationCode());
        assertEquals(11L, logCaptor.getValue().getTargetId());
    }
}
