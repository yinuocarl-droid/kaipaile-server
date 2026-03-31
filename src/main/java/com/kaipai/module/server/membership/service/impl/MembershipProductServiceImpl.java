package com.kaipai.module.server.membership.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.membership.dto.MembershipProductCreateDTO;
import com.kaipai.module.model.membership.dto.MembershipProductQueryDTO;
import com.kaipai.module.model.membership.dto.MembershipProductSortDTO;
import com.kaipai.module.model.membership.dto.MembershipProductStatusChangeDTO;
import com.kaipai.module.model.membership.dto.MembershipProductUpdateDTO;
import com.kaipai.module.model.membership.entity.MembershipProduct;
import com.kaipai.module.server.membership.mapper.MembershipProductMapper;
import com.kaipai.module.server.membership.service.MembershipProductService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MembershipProductServiceImpl extends ServiceImpl<MembershipProductMapper, MembershipProduct> implements MembershipProductService {

    private final AdminOperationLogger adminOperationLogger;

    @Override
    public PageResult<MembershipProduct> adminProductList(MembershipProductQueryDTO query) {
        Page<MembershipProduct> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<MembershipProduct> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getProductCode())) {
            wrapper.like(MembershipProduct::getProductCode, query.getProductCode().trim());
        }
        if (StringUtils.hasText(query.getProductName())) {
            wrapper.like(MembershipProduct::getProductName, query.getProductName().trim());
        }
        if (query.getMembershipTier() != null) {
            wrapper.eq(MembershipProduct::getMembershipTier, query.getMembershipTier());
        }
        if (query.getStatus() != null) {
            wrapper.eq(MembershipProduct::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(MembershipProduct::getSortNo).orderByDesc(MembershipProduct::getLastUpdate);
        Page<MembershipProduct> result = page(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Override
    public MembershipProduct adminProductDetail(Long productId) {
        return requireProduct(productId);
    }

    @Override
    public void createProduct(MembershipProductCreateDTO dto) {
        MembershipProduct product = new MembershipProduct();
        BeanUtils.copyProperties(dto, product);
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

    @Override
    public void updateProduct(Long productId, MembershipProductUpdateDTO dto) {
        MembershipProduct product = requireProduct(productId);
        Map<String, Object> beforeSnapshot = snapshot(product);
        BeanUtils.copyProperties(dto, product);
        product.setLastUpdate(LocalDateTime.now());
        updateById(product);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("membership")
                .operationCode("edit")
                .targetType("membership_product")
                .targetId(product.getProductId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(product))
                .extraContext(snapshot(product))
                .operationResult(1)
                .build());
    }

    @Override
    public void enableProduct(Long productId, MembershipProductStatusChangeDTO dto) {
        changeStatus(productId, 1, "enable", dto == null ? null : dto.getReason());
    }

    @Override
    public void disableProduct(Long productId, MembershipProductStatusChangeDTO dto) {
        changeStatus(productId, 2, "disable", dto == null ? null : dto.getReason());
    }

    @Override
    public void sortProduct(Long productId, MembershipProductSortDTO dto) {
        MembershipProduct product = requireProduct(productId);
        Map<String, Object> beforeSnapshot = snapshot(product);
        product.setSortNo(dto.getSortNo());
        product.setLastUpdate(LocalDateTime.now());
        updateById(product);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("product_id", product.getProductId());
        context.put("product_code", product.getProductCode());
        context.put("sort_no", product.getSortNo());
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("membership")
                .operationCode("sort")
                .targetType("membership_product")
                .targetId(product.getProductId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(product))
                .extraContext(context)
                .operationResult(1)
                .build());
    }

    private MembershipProduct requireProduct(Long productId) {
        MembershipProduct product = getById(productId);
        if (product == null) {
            throw new BizException("会员商品不存在");
        }
        return product;
    }

    private void changeStatus(Long productId, int targetStatus, String operationCode, String reason) {
        MembershipProduct product = requireProduct(productId);
        if (product.getStatus() != null && product.getStatus() == targetStatus) {
            throw new BizException("会员商品状态已匹配");
        }
        Map<String, Object> beforeSnapshot = snapshot(product);
        product.setStatus(targetStatus);
        product.setLastUpdate(LocalDateTime.now());
        updateById(product);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("product_id", product.getProductId());
        context.put("product_code", product.getProductCode());
        context.put("product_status_after", product.getStatus());
        context.put("membership_tier", product.getMembershipTier());
        context.put("price_snapshot_json", snapshot(product));
        context.put("reason", reason);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("membership")
                .operationCode(operationCode)
                .targetType("membership_product")
                .targetId(product.getProductId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(product))
                .extraContext(context)
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
        snapshot.put("benefitConfigJson", product.getBenefitConfigJson());
        return snapshot;
    }
}
