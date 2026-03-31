package com.kaipai.module.server.membership.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.membership.dto.MembershipProductCreateDTO;
import com.kaipai.module.model.membership.dto.MembershipProductQueryDTO;
import com.kaipai.module.model.membership.entity.MembershipProduct;
import com.kaipai.module.server.membership.mapper.MembershipProductMapper;
import com.kaipai.module.server.membership.service.MembershipProductService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MembershipProductServiceImpl extends ServiceImpl<MembershipProductMapper, MembershipProduct> implements MembershipProductService {

    private final AdminOperationLogger adminOperationLogger;

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
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("membership")
                .operationCode("create")
                .targetType("membership_product")
                .targetId(product.getProductId())
                .afterSnapshot(snapshot(product))
                .extraContext(snapshot(product))
                .operationResult(1)
                .build());
    }

    private Map<String, Object> snapshot(MembershipProduct product) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("productId", product.getProductId());
        snapshot.put("productCode", product.getProductCode());
        snapshot.put("productName", product.getProductName());
        snapshot.put("membershipTier", product.getMembershipTier());
        snapshot.put("durationDays", product.getDurationDays());
        snapshot.put("listPrice", product.getListPrice());
        snapshot.put("salePrice", product.getSalePrice());
        snapshot.put("status", product.getStatus());
        snapshot.put("sortNo", product.getSortNo());
        return snapshot;
    }
}
