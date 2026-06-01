package com.kaipai.service.capability;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.model.capability.dto.AdminCapabilityBenefitOverviewDTO;
import com.kaipai.model.capability.dto.CapabilityBenefitSaveDTO;
import com.kaipai.model.capability.dto.CapabilityBenefitStatusChangeDTO;
import com.kaipai.model.capability.dto.CapabilityBenefitQueryDTO;
import com.kaipai.model.capability.dto.CapabilityProductCreateDTO;
import com.kaipai.model.capability.dto.CapabilityProductQueryDTO;
import com.kaipai.model.capability.dto.CapabilityProductSortDTO;
import com.kaipai.model.capability.dto.CapabilityProductStatusChangeDTO;
import com.kaipai.model.capability.dto.CapabilityProductUpdateDTO;
import com.kaipai.model.capability.entity.CapabilityProduct;

public interface CapabilityProductService extends IService<CapabilityProduct> {

    PageResult<CapabilityProduct> adminProductList(CapabilityProductQueryDTO query);

    AdminCapabilityBenefitOverviewDTO adminBenefitOverview(CapabilityBenefitQueryDTO query);

    void createBenefit(CapabilityBenefitSaveDTO dto);

    void updateBenefit(String benefitId, CapabilityBenefitSaveDTO dto);

    void enableBenefit(String benefitId, CapabilityBenefitStatusChangeDTO dto);

    void disableBenefit(String benefitId, CapabilityBenefitStatusChangeDTO dto);

    CapabilityProduct adminProductDetail(Long productId);

    void createProduct(CapabilityProductCreateDTO dto);

    void updateProduct(Long productId, CapabilityProductUpdateDTO dto);

    void enableProduct(Long productId, CapabilityProductStatusChangeDTO dto);

    void disableProduct(Long productId, CapabilityProductStatusChangeDTO dto);

    void sortProduct(Long productId, CapabilityProductSortDTO dto);
}
