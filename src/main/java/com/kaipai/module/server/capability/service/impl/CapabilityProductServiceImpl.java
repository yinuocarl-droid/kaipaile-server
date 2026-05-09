package com.kaipai.module.server.capability.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaipai.common.auth.AdminOperationLogCommand;
import com.kaipai.common.auth.AdminOperationLogger;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.module.model.capability.dto.AdminCapabilityBenefitOverviewDTO;
import com.kaipai.module.model.capability.dto.CapabilityBenefitSaveDTO;
import com.kaipai.module.model.capability.dto.CapabilityBenefitStatusChangeDTO;
import com.kaipai.module.model.capability.dto.CapabilityBenefitQueryDTO;
import com.kaipai.module.model.capability.dto.CapabilityProductCreateDTO;
import com.kaipai.module.model.capability.dto.CapabilityProductQueryDTO;
import com.kaipai.module.model.capability.dto.CapabilityProductSortDTO;
import com.kaipai.module.model.capability.dto.CapabilityProductStatusChangeDTO;
import com.kaipai.module.model.capability.dto.CapabilityProductUpdateDTO;
import com.kaipai.module.model.capability.entity.CapabilityProduct;
import com.kaipai.module.server.card.support.CurrentPhaseShareArtifactSupport;
import com.kaipai.module.server.capability.mapper.CapabilityProductMapper;
import com.kaipai.module.server.capability.service.CapabilityProductService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class CapabilityProductServiceImpl extends ServiceImpl<CapabilityProductMapper, CapabilityProduct> implements CapabilityProductService {

    private final AdminOperationLogger adminOperationLogger;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<CapabilityProduct> adminProductList(CapabilityProductQueryDTO query) {
        Page<CapabilityProduct> page = new Page<>(query.getPageNo(), query.getPageSize());
        LambdaQueryWrapper<CapabilityProduct> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getProductCode())) {
            wrapper.like(CapabilityProduct::getProductCode, query.getProductCode().trim());
        }
        if (StringUtils.hasText(query.getProductName())) {
            wrapper.like(CapabilityProduct::getProductName, query.getProductName().trim());
        }
        if (query.getCapabilityTier() != null) {
            wrapper.eq(CapabilityProduct::getCapabilityTier, query.getCapabilityTier());
        }
        if (query.getStatus() != null) {
            wrapper.eq(CapabilityProduct::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(CapabilityProduct::getSortNo).orderByDesc(CapabilityProduct::getLastUpdate);
        Page<CapabilityProduct> result = page(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Override
    public AdminCapabilityBenefitOverviewDTO adminBenefitOverview(CapabilityBenefitQueryDTO query) {
        LambdaQueryWrapper<CapabilityProduct> wrapper = new LambdaQueryWrapper<>();
        if (query.getCapabilityTier() != null) {
            wrapper.eq(CapabilityProduct::getCapabilityTier, query.getCapabilityTier());
        }
        wrapper.orderByAsc(CapabilityProduct::getSortNo).orderByDesc(CapabilityProduct::getLastUpdate);

        List<CapabilityProduct> products = list(wrapper);
        List<AdminCapabilityBenefitOverviewDTO.BenefitItem> benefitItems = new ArrayList<>();
        Map<String, BenefitCapabilityAccumulator> benefitCapabilityMap = new LinkedHashMap<>();

        for (CapabilityProduct product : products) {
            List<JsonNode> benefitNodes = parseBenefitNodes(product);
            for (int index = 0; index < benefitNodes.size(); index++) {
                AdminCapabilityBenefitOverviewDTO.BenefitItem item = toBenefitItem(product, benefitNodes.get(index), index);
                if (query.getStatus() != null && !Objects.equals(item.getStatus(), query.getStatus())) {
                    continue;
                }
                benefitItems.add(item);
                benefitCapabilityMap.computeIfAbsent(item.getBenefitCode(),
                                ignored -> new BenefitCapabilityAccumulator(item.getBenefitCode(), item.getBenefitName()))
                        .merge(item);
            }
        }

        benefitItems.sort(Comparator.comparing(AdminCapabilityBenefitOverviewDTO.BenefitItem::getCapabilityTier,
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparing(AdminCapabilityBenefitOverviewDTO.BenefitItem::getProductId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(AdminCapabilityBenefitOverviewDTO.BenefitItem::getBenefitCode, Comparator.nullsLast(String::compareTo)));

        AdminCapabilityBenefitOverviewDTO overview = new AdminCapabilityBenefitOverviewDTO();
        overview.setBenefitItems(benefitItems);
        overview.setBenefitCapabilityItems(benefitCapabilityMap.values().stream().map(BenefitCapabilityAccumulator::toDTO).toList());
        return overview;
    }

    @Override
    public void createBenefit(CapabilityBenefitSaveDTO dto) {
        CapabilityProduct product = requireProduct(dto.getProductId());
        ObjectNode root = readBenefitConfigRoot(product);
        ArrayNode benefitItems = benefitItemsArray(root);
        if (findBenefitIndex(benefitItems, dto.getBenefitCode()) >= 0) {
            throw new BizException("能力权益编码已存在");
        }
        benefitItems.add(buildBenefitNode(dto));
        saveBenefitConfig(product, root, "benefit_create", dto.getBenefitCode(), dto.getCapabilitySummary(), null);
    }

    @Override
    public void updateBenefit(String benefitId, CapabilityBenefitSaveDTO dto) {
        BenefitLocator locator = parseBenefitId(benefitId);
        if (!Objects.equals(locator.productId(), dto.getProductId())) {
            throw new BizException("权益归属商品不匹配");
        }
        CapabilityProduct product = requireProduct(locator.productId());
        ObjectNode root = readBenefitConfigRoot(product);
        ArrayNode benefitItems = benefitItemsArray(root);
        int currentIndex = findBenefitIndex(benefitItems, locator.benefitCode());
        if (currentIndex < 0) {
            throw new BizException("能力权益不存在");
        }
        int duplicateIndex = findBenefitIndex(benefitItems, dto.getBenefitCode());
        if (duplicateIndex >= 0 && duplicateIndex != currentIndex) {
            throw new BizException("目标商品下已存在相同权益编码");
        }
        benefitItems.set(currentIndex, buildBenefitNode(dto));
        saveBenefitConfig(product, root, "benefit_edit", dto.getBenefitCode(), dto.getCapabilitySummary(), null);
    }

    @Override
    public void enableBenefit(String benefitId, CapabilityBenefitStatusChangeDTO dto) {
        changeBenefitStatus(benefitId, 1, "benefit_enable", dto == null ? null : dto.getReason());
    }

    @Override
    public void disableBenefit(String benefitId, CapabilityBenefitStatusChangeDTO dto) {
        changeBenefitStatus(benefitId, 2, "benefit_disable", dto == null ? null : dto.getReason());
    }

    @Override
    public CapabilityProduct adminProductDetail(Long productId) {
        return requireProduct(productId);
    }

    @Override
    public void createProduct(CapabilityProductCreateDTO dto) {
        CapabilityProduct product = new CapabilityProduct();
        BeanUtils.copyProperties(dto, product);
        product.setStatus(1);
        save(product);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("capability")
                .operationCode("create")
                .targetType("capability_product")
                .targetId(product.getProductId())
                .afterSnapshot(snapshot(product))
                .extraContext(snapshot(product))
                .operationResult(1)
                .build());
    }

    @Override
    public void updateProduct(Long productId, CapabilityProductUpdateDTO dto) {
        CapabilityProduct product = requireProduct(productId);
        Map<String, Object> beforeSnapshot = snapshot(product);
        BeanUtils.copyProperties(dto, product);
        product.setLastUpdate(LocalDateTime.now());
        updateById(product);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("capability")
                .operationCode("edit")
                .targetType("capability_product")
                .targetId(product.getProductId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(product))
                .extraContext(snapshot(product))
                .operationResult(1)
                .build());
    }

    @Override
    public void enableProduct(Long productId, CapabilityProductStatusChangeDTO dto) {
        changeStatus(productId, 1, "enable", dto == null ? null : dto.getReason());
    }

    @Override
    public void disableProduct(Long productId, CapabilityProductStatusChangeDTO dto) {
        changeStatus(productId, 2, "disable", dto == null ? null : dto.getReason());
    }

    @Override
    public void sortProduct(Long productId, CapabilityProductSortDTO dto) {
        CapabilityProduct product = requireProduct(productId);
        Map<String, Object> beforeSnapshot = snapshot(product);
        product.setSortNo(dto.getSortNo());
        product.setLastUpdate(LocalDateTime.now());
        updateById(product);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("product_id", product.getProductId());
        context.put("product_code", product.getProductCode());
        context.put("sort_no", product.getSortNo());
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("capability")
                .operationCode("sort")
                .targetType("capability_product")
                .targetId(product.getProductId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(product))
                .extraContext(context)
                .operationResult(1)
                .build());
    }

    private CapabilityProduct requireProduct(Long productId) {
        CapabilityProduct product = getById(productId);
        if (product == null) {
            throw new BizException("能力商品不存在");
        }
        return product;
    }

    private void changeBenefitStatus(String benefitId, int targetStatus, String operationCode, String reason) {
        BenefitLocator locator = parseBenefitId(benefitId);
        CapabilityProduct product = requireProduct(locator.productId());
        ObjectNode root = readBenefitConfigRoot(product);
        ArrayNode benefitItems = benefitItemsArray(root);
        int index = findBenefitIndex(benefitItems, locator.benefitCode());
        if (index < 0) {
            throw new BizException("能力权益不存在");
        }
        JsonNode rawNode = benefitItems.get(index);
        ObjectNode benefitNode = rawNode instanceof ObjectNode objectNode ? objectNode.deepCopy() : objectMapper.createObjectNode();
        Integer currentStatus = firstInteger(benefitNode, "status");
        if (Objects.equals(currentStatus, targetStatus)) {
            throw new BizException("能力权益状态已匹配");
        }
        benefitNode.put("status", targetStatus);
        benefitItems.set(index, benefitNode);
        saveBenefitConfig(product, root, operationCode, locator.benefitCode(),
                firstText(benefitNode, "capabilitySummary"),
                reason);
    }

    private void changeStatus(Long productId, int targetStatus, String operationCode, String reason) {
        CapabilityProduct product = requireProduct(productId);
        if (product.getStatus() != null && product.getStatus() == targetStatus) {
            throw new BizException("能力商品状态已匹配");
        }
        Map<String, Object> beforeSnapshot = snapshot(product);
        product.setStatus(targetStatus);
        product.setLastUpdate(LocalDateTime.now());
        updateById(product);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("product_id", product.getProductId());
        context.put("product_code", product.getProductCode());
        context.put("product_status_after", product.getStatus());
        context.put("capability_tier", product.getCapabilityTier());
        context.put("price_snapshot_json", snapshot(product));
        context.put("reason", reason);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("capability")
                .operationCode(operationCode)
                .targetType("capability_product")
                .targetId(product.getProductId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(product))
                .extraContext(context)
                .operationResult(1)
                .build());
    }

    private Map<String, Object> snapshot(CapabilityProduct product) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("productId", product.getProductId());
        snapshot.put("productCode", product.getProductCode());
        snapshot.put("productName", product.getProductName());
        snapshot.put("capabilityTier", product.getCapabilityTier());
        snapshot.put("durationDays", product.getDurationDays());
        snapshot.put("listPrice", product.getListPrice());
        snapshot.put("salePrice", product.getSalePrice());
        snapshot.put("status", product.getStatus());
        snapshot.put("sortNo", product.getSortNo());
        snapshot.put("benefitConfigJson", product.getBenefitConfigJson());
        return snapshot;
    }

    private ObjectNode readBenefitConfigRoot(CapabilityProduct product) {
        if (!StringUtils.hasText(product.getBenefitConfigJson())) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode root = objectMapper.readTree(product.getBenefitConfigJson());
            if (root instanceof ObjectNode objectNode) {
                JsonNode benefitItems = objectNode.path("benefitItems");
                if (benefitItems.isMissingNode() || benefitItems.isArray()) {
                    return objectNode.deepCopy();
                }
                throw new BizException("能力权益配置 benefitItems 必须是数组");
            }
            throw new BizException("能力权益配置必须是对象");
        } catch (Exception ex) {
            throw new BizException("能力权益配置 JSON 无法解析");
        }
    }

    private ArrayNode benefitItemsArray(ObjectNode root) {
        JsonNode current = root.get("benefitItems");
        if (current instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        if (current != null && !current.isMissingNode()) {
            throw new BizException("能力权益配置 benefitItems 必须是数组");
        }
        ArrayNode created = objectMapper.createArrayNode();
        root.set("benefitItems", created);
        return created;
    }

    private int findBenefitIndex(ArrayNode benefitItems, String benefitCode) {
        String normalizedBenefitCode = benefitCode == null ? null : benefitCode.trim();
        for (int i = 0; i < benefitItems.size(); i++) {
            JsonNode node = benefitItems.get(i);
            String currentCode = firstText(node, "benefitCode");
            if (Objects.equals(currentCode, normalizedBenefitCode)) {
                return i;
            }
        }
        return -1;
    }

    private ObjectNode buildBenefitNode(CapabilityBenefitSaveDTO dto) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("benefitCode", dto.getBenefitCode().trim());
        node.put("benefitName", dto.getBenefitName().trim());
        if (StringUtils.hasText(dto.getCapabilitySummary())) {
            node.put("capabilitySummary", dto.getCapabilitySummary().trim());
        }
        node.put("status", dto.getStatus());
        node.set("affectedPages", objectMapper.valueToTree(dto.getAffectedPages() == null ? Collections.emptyList() : dto.getAffectedPages()));
        node.set("artifactTypes", objectMapper.valueToTree(normalizeArtifactTypes(dto.getArtifactTypes())));
        return node;
    }

    private void saveBenefitConfig(CapabilityProduct product,
                                   ObjectNode root,
                                   String operationCode,
                                   String benefitCode,
                                   String capabilitySummary,
                                   String reason) {
        Map<String, Object> beforeSnapshot = snapshot(product);
        try {
            product.setBenefitConfigJson(objectMapper.writeValueAsString(root));
        } catch (Exception ex) {
            throw new BizException("能力权益配置 JSON 无法序列化");
        }
        product.setLastUpdate(LocalDateTime.now());
        updateById(product);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("product_id", product.getProductId());
        context.put("product_code", product.getProductCode());
        context.put("capability_tier", product.getCapabilityTier());
        context.put("benefit_code", benefitCode);
        context.put("capability_summary", capabilitySummary);
        context.put("reason", reason);
        adminOperationLogger.log(AdminOperationLogCommand.builder()
                .moduleCode("capability")
                .operationCode(operationCode)
                .targetType("capability_product")
                .targetId(product.getProductId())
                .beforeSnapshot(beforeSnapshot)
                .afterSnapshot(snapshot(product))
                .extraContext(context)
                .operationResult(1)
                .build());
    }

    private List<JsonNode> parseBenefitNodes(CapabilityProduct product) {
        if (!StringUtils.hasText(product.getBenefitConfigJson())) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = objectMapper.readTree(product.getBenefitConfigJson());
            if (root == null || root.isNull()) {
                return Collections.emptyList();
            }
            if (!root.isObject()) {
                throw new BizException("能力权益配置必须是对象");
            }
            JsonNode arrayNode = root.path("benefitItems");
            if (arrayNode.isMissingNode()) {
                return Collections.emptyList();
            }
            if (!arrayNode.isArray()) {
                throw new BizException("能力权益配置 benefitItems 必须是数组");
            }
            List<JsonNode> nodes = new ArrayList<>();
            arrayNode.forEach(nodes::add);
            return nodes;
        } catch (Exception ex) {
            log.warn("capability benefit config rejected for productId={}", product.getProductId(), ex);
            throw new BizException("能力权益配置不是当前结构");
        }
    }

    private String buildBenefitId(Long productId, String benefitCode) {
        return productId + ":" + benefitCode;
    }

    private BenefitLocator parseBenefitId(String benefitId) {
        if (!StringUtils.hasText(benefitId) || !benefitId.contains(":")) {
            throw new BizException("能力权益标识不合法");
        }
        String[] parts = benefitId.split(":", 2);
        try {
            return new BenefitLocator(Long.parseLong(parts[0]), parts[1]);
        } catch (NumberFormatException ex) {
            throw new BizException("能力权益标识不合法");
        }
    }

    private AdminCapabilityBenefitOverviewDTO.BenefitItem toBenefitItem(CapabilityProduct product, JsonNode node, int index) {
        AdminCapabilityBenefitOverviewDTO.BenefitItem item = new AdminCapabilityBenefitOverviewDTO.BenefitItem();
        Integer itemStatus = firstInteger(node, "status");
        if (itemStatus == null) {
            throw new BizException("能力权益 status 缺失");
        }
        item.setBenefitId(buildBenefitId(product.getProductId(), resolveBenefitCode(product, node, index)));
        item.setProductId(product.getProductId());
        item.setProductCode(product.getProductCode());
        item.setProductName(product.getProductName());
        item.setBenefitCode(resolveBenefitCode(product, node, index));
        item.setBenefitName(resolveBenefitName(product, node, index));
        item.setCapabilityTier(product.getCapabilityTier());
        item.setCapabilitySummary(firstText(node, "capabilitySummary"));
        item.setStatus(itemStatus);
        item.setLastUpdate(product.getLastUpdate());
        item.setAffectedPages(firstTextList(node, "affectedPages"));
        item.setArtifactTypes(normalizeArtifactTypes(firstTextList(node, "artifactTypes")));
        return item;
    }

    private String resolveBenefitCode(CapabilityProduct product, JsonNode node, int index) {
        String benefitCode = firstText(node, "benefitCode");
        if (StringUtils.hasText(benefitCode)) {
            return benefitCode;
        }
        throw new BizException("能力权益 benefitCode 缺失");
    }

    private String resolveBenefitName(CapabilityProduct product, JsonNode node, int index) {
        String benefitName = firstText(node, "benefitName");
        if (StringUtils.hasText(benefitName)) {
            return benefitName;
        }
        throw new BizException("能力权益 benefitName 缺失");
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isValueNode() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private Integer firstInteger(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isInt() || value.isLong()) {
                return value.asInt();
            }
        }
        return null;
    }

    private List<String> firstTextList(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isArray()) {
                List<String> result = new ArrayList<>();
                value.forEach(item -> {
                    if (item.isValueNode() && StringUtils.hasText(item.asText())) {
                        result.add(item.asText().trim());
                    }
                });
                return result;
            }
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                return List.of(value.asText().trim());
            }
        }
        return Collections.emptyList();
    }

    private List<String> normalizeArtifactTypes(List<String> artifactTypes) {
        if (artifactTypes == null || artifactTypes.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalized = new ArrayList<>();
        for (String artifactType : artifactTypes) {
            normalized.add(CurrentPhaseShareArtifactSupport.requireArtifactType(artifactType, "artifactTypes"));
        }
        return normalized;
    }

    private static class BenefitCapabilityAccumulator {
        private final String benefitCode;
        private final String benefitName;
        private final Set<Long> relatedProductIds = new LinkedHashSet<>();
        private final Set<String> relatedProductCodes = new LinkedHashSet<>();
        private final Set<String> affectedPages = new LinkedHashSet<>();
        private final Set<String> artifactTypes = new LinkedHashSet<>();
        private final Map<Integer, Boolean> tierEnabledMap = new LinkedHashMap<>();
        private String capabilitySummary;

        private BenefitCapabilityAccumulator(String benefitCode, String benefitName) {
            this.benefitCode = benefitCode;
            this.benefitName = benefitName;
        }

        private void merge(AdminCapabilityBenefitOverviewDTO.BenefitItem item) {
            if (!StringUtils.hasText(capabilitySummary) && StringUtils.hasText(item.getCapabilitySummary())) {
                capabilitySummary = item.getCapabilitySummary();
            }
            if (item.getProductId() != null) {
                relatedProductIds.add(item.getProductId());
            }
            if (StringUtils.hasText(item.getProductCode())) {
                relatedProductCodes.add(item.getProductCode());
            }
            affectedPages.addAll(item.getAffectedPages());
            artifactTypes.addAll(item.getArtifactTypes());
            if (item.getCapabilityTier() != null) {
                tierEnabledMap.put(item.getCapabilityTier(), Objects.equals(item.getStatus(), 1));
            }
        }

        private AdminCapabilityBenefitOverviewDTO.BenefitCapabilityItem toDTO() {
            AdminCapabilityBenefitOverviewDTO.BenefitCapabilityItem item = new AdminCapabilityBenefitOverviewDTO.BenefitCapabilityItem();
            item.setBenefitCode(benefitCode);
            item.setBenefitName(benefitName);
            item.setCapabilitySummary(capabilitySummary);
            item.setTierEnabledMap(tierEnabledMap);
            item.setRelatedProductIds(new ArrayList<>(relatedProductIds));
            item.setRelatedProductCodes(new ArrayList<>(relatedProductCodes));
            item.setAffectedPages(new ArrayList<>(affectedPages));
            item.setArtifactTypes(new ArrayList<>(artifactTypes));
            return item;
        }
    }

    private record BenefitLocator(Long productId, String benefitCode) {
    }
}
