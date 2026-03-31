package com.kaipai.module.server.membership.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.module.model.membership.dto.AdminMembershipBenefitOverviewDTO;
import com.kaipai.module.model.membership.dto.MembershipBenefitQueryDTO;
import com.kaipai.module.model.membership.dto.MembershipBenefitSaveDTO;
import com.kaipai.module.model.membership.entity.MembershipProduct;
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
class MembershipProductServiceImplTest {

    @Mock
    private AdminOperationLogger adminOperationLogger;

    private MembershipProductServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new MembershipProductServiceImpl(adminOperationLogger, new ObjectMapper()));
    }

    @Test
    void adminBenefitOverviewShouldBuildBenefitItemsAndCapabilityMatrix() {
        MembershipProduct product = new MembershipProduct();
        product.setProductId(10L);
        product.setProductCode("vip-year");
        product.setProductName("VIP Annual");
        product.setMembershipTier(2);
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

        AdminMembershipBenefitOverviewDTO overview = service.adminBenefitOverview(new MembershipBenefitQueryDTO());

        assertEquals(1, overview.getBenefitItems().size());
        AdminMembershipBenefitOverviewDTO.BenefitItem item = overview.getBenefitItems().get(0);
        assertEquals("10:invite_priority", item.getBenefitId());
        assertEquals("invite_priority", item.getBenefitCode());
        assertEquals(List.of("referral", "user-center"), item.getAffectedPages());

        assertEquals(1, overview.getCapabilityMatrix().size());
        AdminMembershipBenefitOverviewDTO.CapabilityMatrixItem matrixItem = overview.getCapabilityMatrix().get(0);
        assertEquals(Boolean.TRUE, matrixItem.getTierEnabledMap().get(2));
        assertEquals(List.of("vip-year"), matrixItem.getRelatedProductCodes());
    }

    @Test
    void createBenefitShouldAppendBenefitAndLogOperation() {
        MembershipProduct product = new MembershipProduct();
        product.setProductId(11L);
        product.setProductCode("member-quarter");
        product.setProductName("Member Quarter");
        product.setMembershipTier(1);
        product.setStatus(1);
        product.setBenefitConfigJson("{\"benefitItems\":[]}");

        MembershipBenefitSaveDTO dto = new MembershipBenefitSaveDTO();
        dto.setProductId(11L);
        dto.setBenefitCode("resume_boost");
        dto.setBenefitName("Resume Boost");
        dto.setCapabilitySummary("提升简历曝光");
        dto.setStatus(1);
        dto.setAffectedPages(List.of("profile"));
        dto.setArtifactTypes(List.of("resume-card"));

        doReturn(product).when(service).getById(11L);
        doReturn(true).when(service).updateById(any(MembershipProduct.class));

        service.createBenefit(dto);

        ArgumentCaptor<MembershipProduct> productCaptor = ArgumentCaptor.forClass(MembershipProduct.class);
        verify(service).updateById(productCaptor.capture());
        assertTrue(productCaptor.getValue().getBenefitConfigJson().contains("\"benefitCode\":\"resume_boost\""));

        ArgumentCaptor<AdminOperationLogCommand> logCaptor = ArgumentCaptor.forClass(AdminOperationLogCommand.class);
        verify(adminOperationLogger).log(logCaptor.capture());
        assertEquals("benefit_create", logCaptor.getValue().getOperationCode());
        assertEquals(11L, logCaptor.getValue().getTargetId());
    }
}
