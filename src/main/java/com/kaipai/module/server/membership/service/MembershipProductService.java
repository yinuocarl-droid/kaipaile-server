package com.kaipai.module.server.membership.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.membership.dto.MembershipProductCreateDTO;
import com.kaipai.module.model.membership.dto.MembershipProductQueryDTO;
import com.kaipai.module.model.membership.entity.MembershipProduct;

public interface MembershipProductService extends IService<MembershipProduct> {

    PageResult<MembershipProduct> adminProductList(MembershipProductQueryDTO query);

    void createProduct(MembershipProductCreateDTO dto);
}
