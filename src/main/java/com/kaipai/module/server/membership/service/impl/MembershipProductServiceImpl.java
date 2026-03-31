package com.kaipai.module.server.membership.service.impl;

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
import com.kaipai.module.model.membership.dto.AdminMembershipBenefitOverviewDTO;
import com.kaipai.module.model.membership.dto.MembershipBenefitSaveDTO;
import com.kaipai.module.model.membership.dto.MembershipBenefitStatusChangeDTO;
import com.kaipai.module.model.membership.dto.MembershipBenefitQueryDTO;
import com.kaipai.module.model.membership.dto.MembershipProductCreateDTO;
import com.kaipai.module.model.membership.dto.MembershipProductQueryDTO;
import com.kaipai.module.model.membership.dto.MembershipProductSortDTO;
import com.kaipai.module.model.membership.dto.MembershipProductStatusChangeDTO;
import com.kaipai.module.model.membership.dto.MembershipProductUpdateDTO;
import com.kaipai.module.model.membership.entity.MembershipProduct;
import com.kaipai.module.server.membership.mapper.MembershipProductMapper;
import com.kaipai.module.server.membership.service.MembershipProductService;
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
public class MembershipProductServiceImpl extends ServiceImpl<MembershipProductMapper, MembershipProduct> implements MembershipProductService {

    private static final List<String> BENEFIT_ARRAY_FIELDS = List.of("benefitItems", "benefits", "items", "capabilityMatrix");

    private final AdminOperationLogger adminOperationLogger;
    private final ObjectMapper objectMapper;

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
    public AdminMembershipBenefitOverviewDTO adminBenefitOverview(MembershipBenefitQueryDTO query) {
        LambdaQueryWrapper<MembershipProduct> wrapper = new LambdaQueryWrapper<>();
        if (query.getMembershipTier() != null) {
            wrapper.eq(MembershipProduct::getMembershipTier, query.getMembershipTier());
        }
        wrapper.orderByAsc(MembershipProduct::getSortNo).orderByDesc(MembershipProduct::getLastUpdate);

        List<MembershipProduct> products = list(wrapper);
        List<AdminMembershipBenefitOverviewDTO.BenefitItem> benefitItems = new ArrayList<>();
        Map<String, CapabilityMatrixAccumulator> capabilityMap = new LinkedHashMap<>();

        for (MembershipProduct product : products) {
            List<JsonNode> benefitNodes = parseBenefitNodes(product);
            for (int index = 0; index < benefitNodes.size(); index++) {
                AdminMembershipBenefitOverviewDTO.BenefitItem item = toBenefitItem(product, benefitNodes.get(index), index);
                if (query.getStatus() != null && !Objects.equals(item.getStatus(), query.getStatus())) {
                    continue;
                }
                benefitItems.add(item);
                capabilityMap.computeIfAbsent(item.getBenefitCode(),
                                ignored -> new CapabilityMatrixAccumulator(item.getBenefitCode(), item.getBenefitName()))
                        .merge(item);
            }
        }

        benefitItems.sort(Comparator.comparing(AdminMembershipBenefitOverviewDTO.BenefitItem::getMembershipTier,
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparing(AdminMembershipBenefitOverviewDTO.BenefitItem::getProductId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(AdminMembershipBenefitOverviewDTO.BenefitItem::getBenefitCode, Comparator.nullsLast(String::compareTo)));

        AdminMembershipBenefitOverviewDTO overview = new AdminMembershipBenefitOverviewDTO();
        overview.setBenefitItems(benefitItems);
        overview.setCapabilityMatrix(capabilityMap.values().stream().map(CapabilityMatrixAccumulator::toDTO).toList());
        return overview;
    }

    @Override
    public void createBenefit(MembershipBenefitSaveDTO dto) {
        MembershipProduct product = requireProduct(dto.getProductId());
        ObjectNode root = readBenefitConfigRoot(product);
        ArrayNode benefitItems = benefitItemsArray(root);
        if (findBenefitIndex(benefitItems, dto.getBenefitCode()) >= 0) {
            throw new BizException("会员权益编码已存在");
        }
        benefitItems.add(buildBenefitNode(dto));
        saveBenefitConfig(product, root, "benefit_create", dto.getBenefitCode(), dto.getCapabilitySummary(), null);
    }

    @Override
    public void updateBenefit(String benefitId, MembershipBenefitSaveDTO dto) {
        BenefitLocator locator = parseBenefitId(benefitId);
        if (!Objects.equals(locator.productId(), dto.getProductId())) {
            throw new BizException("权益归属商品不匹配");
        }
        MembershipProduct product = requireProduct(locator.productId());
        ObjectNode root = readBenefitConfigRoot(product);
        ArrayNode benefitItems = benefitItemsArray(root);
        int currentIndex = findBenefitIndex(benefitItems, locator.benefitCode());
        if (currentIndex < 0) {
            throw new BizException("会员权益不存在");
        }
        int duplicateIndex = findBenefitIndex(benefitItems, dto.getBenefitCode());
        if (duplicateIndex >= 0 && duplicateIndex != currentIndex) {
            throw new BizException("目标商品下已存在相同权益编码");
        }
        benefitItems.set(currentIndex, buildBenefitNode(dto));
        saveBenefitConfig(product, root, "benefit_edit", dto.getBenefitCode(), dto.getCapabilitySummary(), null);
    }

    @Override
    public void enableBenefit(String benefitId, MembershipBenefitStatusChangeDTO dto) {
        changeBenefitStatus(benefitId, 1, "benefit_enable", dto == null ? null : dto.getReason());
    }

    @Override
    public void disableBenefit(String benefitId, MembershipBenefitStatusChangeDTO dto) {
        changeBenefitStatus(benefitId, 2, "benefit_disable", dto == null ? null : dto.getReason());
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

    private void changeBenefitStatus(String benefitId, int targetStatus, String operationCode, String reason) {
        BenefitLocator locator = parseBenefitId(benefitId);
        MembershipProduct product = requireProduct(locator.productId());
        ObjectNode root = readBenefitConfigRoot(product);
        ArrayNode benefitItems = benefitItemsArray(root);
        int index = findBenefitIndex(benefitItems, locator.benefitCode());
        if (index < 0) {
            throw new BizException("会员权益不存在");
        }
        JsonNode rawNode = benefitItems.get(index);
        ObjectNode benefitNode = rawNode instanceof ObjectNode objectNode ? objectNode.deepCopy() : objectMapper.createObjectNode();
        Integer currentStatus = firstInteger(benefitNode, "status", "benefitStatus", "enabledStatus", "state", "activeStatus", "isEnabled");
        if (Objects.equals(currentStatus, targetStatus)) {
            throw new BizException("会员权益状态已匹配");
        }
        benefitNode.put("status", targetStatus);
        benefitItems.set(index, benefitNode);
        saveBenefitConfig(product, root, operationCode, locator.benefitCode(),
                firstText(benefitNode, "capabilitySummary", "summary", "description", "capabilityDesc", "abilityDesc"),
                reason);
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

    private ObjectNode readBenefitConfigRoot(MembershipProduct product) {
        if (!StringUtils.hasText(product.getBenefitConfigJson())) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode root = objectMapper.readTree(product.getBenefitConfigJson());
            if (root instanceof ObjectNode objectNode) {
                return objectNode.deepCopy();
            }
            if (root instanceof ArrayNode arrayNode) {
                ObjectNode objectNode = objectMapper.createObjectNode();
                objectNode.set("benefitItems", arrayNode.deepCopy());
                return objectNode;
            }
            return objectMapper.createObjectNode();
        } catch (Exception ex) {
            throw new BizException("会员权益配置 JSON 无法解析");
        }
    }

    private ArrayNode benefitItemsArray(ObjectNode root) {
        JsonNode current = root.get("benefitItems");
        if (current instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        for (String fieldName : BENEFIT_ARRAY_FIELDS) {
            JsonNode value = root.get(fieldName);
            if (value instanceof ArrayNode arrayNode) {
                ArrayNode copied = arrayNode.deepCopy();
                root.set("benefitItems", copied);
                if (!Objects.equals(fieldName, "benefitItems")) {
                    root.remove(fieldName);
                }
                return copied;
            }
        }
        ArrayNode created = objectMapper.createArrayNode();
        root.set("benefitItems", created);
        return created;
    }

    private int findBenefitIndex(ArrayNode benefitItems, String benefitCode) {
        for (int i = 0; i < benefitItems.size(); i++) {
            JsonNode node = benefitItems.get(i);
            String currentCode = firstText(node, "benefitCode", "code", "capabilityCode", "abilityCode");
            if (Objects.equals(currentCode, benefitCode)) {
                return i;
            }
        }
        return -1;
    }

    private ObjectNode buildBenefitNode(MembershipBenefitSaveDTO dto) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("benefitCode", dto.getBenefitCode().trim());
        node.put("benefitName", dto.getBenefitName().trim());
        if (StringUtils.hasText(dto.getCapabilitySummary())) {
            node.put("capabilitySummary", dto.getCapabilitySummary().trim());
        }
        node.put("status", dto.getStatus());
        node.set("affectedPages", objectMapper.valueToTree(dto.getAffectedPages() == null ? Collections.emptyList() : dto.getAffectedPages()));
        node.set("artifactTypes", objectMapper.valueToTree(dto.getArtifactTypes() == null ? Collections.emptyList() : dto.getArtifactTypes()));
        return node;
    }

    private void saveBenefitConfig(MembershipProduct product,
                                   ObjectNode root,
                                   String operationCode,
                                   String benefitCode,
                                   String capabilitySummary,
                                   String reason) {
        Map<String, Object> beforeSnapshot = snapshot(product);
        try {
            product.setBenefitConfigJson(objectMapper.writeValueAsString(root));
        } catch (Exception ex) {
            throw new BizException("会员权益配置 JSON 无法序列化");
        }
        product.setLastUpdate(LocalDateTime.now());
        updateById(product);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("product_id", product.getProductId());
        context.put("product_code", product.getProductCode());
        context.put("membership_tier", product.getMembershipTier());
        context.put("benefit_code", benefitCode);
        context.put("capability_summary", capabilitySummary);
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

    private List<JsonNode> parseBenefitNodes(MembershipProduct product) {
        if (!StringUtils.hasText(product.getBenefitConfigJson())) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = objectMapper.readTree(product.getBenefitConfigJson());
            if (root == null || root.isNull()) {
                return Collections.emptyList();
            }
            if (root.isArray()) {
                List<JsonNode> nodes = new ArrayList<>();
                root.forEach(nodes::add);
                return nodes;
            }
            for (String fieldName : List.of("benefitItems", "benefits", "items", "capabilityMatrix")) {
                JsonNode arrayNode = root.path(fieldName);
                if (arrayNode.isArray()) {
                    List<JsonNode> nodes = new ArrayList<>();
                    arrayNode.forEach(nodes::add);
                    return nodes;
                }
            }
            return looksLikeBenefitNode(root) ? Collections.singletonList(root) : Collections.emptyList();
        } catch (Exception ex) {
            log.warn("failed to parse membership benefit config for productId={}", product.getProductId(), ex);
            return Collections.emptyList();
        }
    }

    private boolean looksLikeBenefitNode(JsonNode node) {
        return StringUtils.hasText(firstText(node, "benefitCode", "code", "capabilityCode", "abilityCode"))
                || StringUtils.hasText(firstText(node, "benefitName", "name", "capabilityName", "title"))
                || StringUtils.hasText(firstText(node, "capabilitySummary", "summary", "description", "capabilityDesc"));
    }

    private String buildBenefitId(Long productId, String benefitCode) {
        return productId + ":" + benefitCode;
    }

    private BenefitLocator parseBenefitId(String benefitId) {
        if (!StringUtils.hasText(benefitId) || !benefitId.contains(":")) {
            throw new BizException("会员权益标识不合法");
        }
        String[] parts = benefitId.split(":", 2);
        try {
            return new BenefitLocator(Long.parseLong(parts[0]), parts[1]);
        } catch (NumberFormatException ex) {
            throw new BizException("会员权益标识不合法");
        }
    }

    private AdminMembershipBenefitOverviewDTO.BenefitItem toBenefitItem(MembershipProduct product, JsonNode node, int index) {
        AdminMembershipBenefitOverviewDTO.BenefitItem item = new AdminMembershipBenefitOverviewDTO.BenefitItem();
        Integer itemStatus = firstInteger(node, "status", "benefitStatus", "enabledStatus", "state", "activeStatus", "isEnabled");
        item.setBenefitId(buildBenefitId(product.getProductId(), resolveBenefitCode(product, node, index)));
        item.setProductId(product.getProductId());
        item.setProductCode(product.getProductCode());
        item.setProductName(product.getProductName());
        item.setBenefitCode(resolveBenefitCode(product, node, index));
        item.setBenefitName(resolveBenefitName(product, node, index));
        item.setMembershipTier(product.getMembershipTier());
        item.setCapabilitySummary(firstText(node, "capabilitySummary", "summary", "description", "capabilityDesc", "abilityDesc"));
        item.setStatus(itemStatus != null ? itemStatus : product.getStatus());
        item.setLastUpdate(product.getLastUpdate());
        item.setAffectedPages(firstTextList(node, "affectedPages", "pageScopes", "pages", "scenes"));
        item.setArtifactTypes(firstTextList(node, "artifactTypes", "artifactScopes", "artifacts", "outputs"));
        return item;
    }

    private String resolveBenefitCode(MembershipProduct product, JsonNode node, int index) {
        String benefitCode = firstText(node, "benefitCode", "code", "capabilityCode", "abilityCode");
        if (StringUtils.hasText(benefitCode)) {
            return benefitCode;
        }
        return product.getProductCode() + "#" + (index + 1);
    }

    private String resolveBenefitName(MembershipProduct product, JsonNode node, int index) {
        String benefitName = firstText(node, "benefitName", "name", "capabilityName", "title");
        if (StringUtils.hasText(benefitName)) {
            return benefitName;
        }
        if (StringUtils.hasText(product.getProductName())) {
            return product.getProductName() + "-" + (index + 1);
        }
        return "benefit-" + (index + 1);
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
            if (value.isBoolean()) {
                return value.asBoolean() ? 1 : 2;
            }
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                try {
                    return Integer.parseInt(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    // ignore malformed numeric strings
                }
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

    private static class CapabilityMatrixAccumulator {
        private final String capabilityCode;
        private final String capabilityName;
        private final Set<Long> relatedProductIds = new LinkedHashSet<>();
        private final Set<String> relatedProductCodes = new LinkedHashSet<>();
        private final Set<String> affectedPages = new LinkedHashSet<>();
        private final Set<String> artifactTypes = new LinkedHashSet<>();
        private final Map<Integer, Boolean> tierEnabledMap = new LinkedHashMap<>();
        private String capabilitySummary;

        private CapabilityMatrixAccumulator(String capabilityCode, String capabilityName) {
            this.capabilityCode = capabilityCode;
            this.capabilityName = capabilityName;
        }

        private void merge(AdminMembershipBenefitOverviewDTO.BenefitItem item) {
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
            if (item.getMembershipTier() != null) {
                tierEnabledMap.put(item.getMembershipTier(), Objects.equals(item.getStatus(), 1));
            }
        }

        private AdminMembershipBenefitOverviewDTO.CapabilityMatrixItem toDTO() {
            AdminMembershipBenefitOverviewDTO.CapabilityMatrixItem item = new AdminMembershipBenefitOverviewDTO.CapabilityMatrixItem();
            item.setCapabilityCode(capabilityCode);
            item.setCapabilityName(capabilityName);
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
