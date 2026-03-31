package com.kaipai.module.server.membership.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.membership.dto.AdminMembershipBenefitOverviewDTO;
import com.kaipai.module.model.membership.dto.MembershipBenefitQueryDTO;
import com.kaipai.module.model.membership.dto.MembershipProductCreateDTO;
import com.kaipai.module.model.membership.dto.MembershipProductQueryDTO;
import com.kaipai.module.model.membership.dto.MembershipProductSortDTO;
import com.kaipai.module.model.membership.dto.MembershipProductStatusChangeDTO;
import com.kaipai.module.model.membership.dto.MembershipProductUpdateDTO;
import com.kaipai.module.model.membership.entity.MembershipProduct;

public interface MembershipProductService extends IService<MembershipProduct> {

    PageResult<MembershipProduct> adminProductList(MembershipProductQueryDTO query);

    AdminMembershipBenefitOverviewDTO adminBenefitOverview(MembershipBenefitQueryDTO query);

    MembershipProduct adminProductDetail(Long productId);

    void createProduct(MembershipProductCreateDTO dto);

    void updateProduct(Long productId, MembershipProductUpdateDTO dto);

    void enableProduct(Long productId, MembershipProductStatusChangeDTO dto);

    void disableProduct(Long productId, MembershipProductStatusChangeDTO dto);

    void sortProduct(Long productId, MembershipProductSortDTO dto);
}
