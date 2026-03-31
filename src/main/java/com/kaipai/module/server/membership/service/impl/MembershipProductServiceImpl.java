package com.kaipai.module.server.membership.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.membership.dto.MembershipProductCreateDTO;
import com.kaipai.module.model.membership.dto.MembershipProductQueryDTO;
import com.kaipai.module.model.membership.entity.MembershipProduct;
import com.kaipai.module.server.membership.mapper.MembershipProductMapper;
import com.kaipai.module.server.membership.service.MembershipProductService;
import org.springframework.stereotype.Service;

@Service
public class MembershipProductServiceImpl extends ServiceImpl<MembershipProductMapper, MembershipProduct> implements MembershipProductService {

    @Override
    public PageResult<MembershipProduct> adminProductList(MembershipProductQueryDTO query) {
        Page<MembershipProduct> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<MembershipProduct> wrapper = new LambdaQueryWrapper<>();
        if (query.getMembershipTier() != null) {
            wrapper.eq(MembershipProduct::getMembershipTier, query.getMembershipTier());
        }
        if (query.getStatus() != null) {
            wrapper.eq(MembershipProduct::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(MembershipProduct::getCreateTime);
        Page<MembershipProduct> result = page(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Override
    public void createProduct(MembershipProductCreateDTO dto) {
        MembershipProduct product = new MembershipProduct();
        product.setProductCode(dto.getProductCode());
        product.setProductName(dto.getProductName());
        product.setMembershipTier(dto.getMembershipTier());
        product.setDurationDays(dto.getDurationDays());
        product.setListPrice(dto.getListPrice());
        product.setSalePrice(dto.getSalePrice());
        product.setBenefitConfigJson(dto.getBenefitConfigJson());
        product.setSortNo(dto.getSortNo());
        product.setStatus(1);
        save(product);
    }
}
