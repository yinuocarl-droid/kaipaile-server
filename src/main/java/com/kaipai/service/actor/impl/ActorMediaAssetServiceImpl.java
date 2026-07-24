package com.kaipai.service.actor.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kaipai.common.exception.BizException;
import com.kaipai.common.result.PageResult;
import com.kaipai.common.result.ResultCode;
import com.kaipai.mapper.actor.*; import com.kaipai.mapper.card.ShareCardAssetMapper; import com.kaipai.model.actor.dto.*; import com.kaipai.model.actor.entity.*; import com.kaipai.model.card.entity.ShareCardAsset; import com.kaipai.service.actor.*;
import java.time.Duration; import java.util.*; import java.util.function.Function; import java.util.stream.Collectors; import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Propagation; import org.springframework.transaction.annotation.Transactional; import org.springframework.util.StringUtils; import org.springframework.web.multipart.MultipartFile;
@Service @RequiredArgsConstructor
public class ActorMediaAssetServiceImpl implements ActorMediaAssetService {
    private final ActorMediaAssetMapper assetMapper; private final ActorProfileMapper profileMapper; private final ActorProfileAssetMapper profileAssetMapper; private final ActorWorkAssetMapper workAssetMapper; private final ActorExperienceMapper experienceMapper; private final ShareCardAssetMapper shareAssetMapper; private final ActorMediaAssetPageMapper pageMapper; private final PrivateActorMediaStorage storage; private final ActorPrivatePdfProcessor pdfProcessor;
    public PageResult<ActorAssetRespDTO> list(Long userId, ActorAssetQueryDTO query) { long pageNo=Math.max(1,query.getPage()); long size=query.getSize()<=0?10:Math.min(query.getSize(),50); LambdaQueryWrapper<ActorMediaAsset> wrapper=new LambdaQueryWrapper<ActorMediaAsset>().eq(ActorMediaAsset::getUserId,userId); if(StringUtils.hasText(query.getMediaType()))wrapper.eq(ActorMediaAsset::getMediaType,query.getMediaType().trim()); if(StringUtils.hasText(query.getCategoryCode()))wrapper.eq(ActorMediaAsset::getCategoryCode,query.getCategoryCode().trim()); if(StringUtils.hasText(query.getProcessStatus()))wrapper.eq(ActorMediaAsset::getProcessStatus,query.getProcessStatus().trim()); if(StringUtils.hasText(query.getKeyword()))wrapper.like(ActorMediaAsset::getOriginalName,query.getKeyword().trim()); wrapper.orderByDesc(ActorMediaAsset::getAssetId); Page<ActorMediaAsset> result=assetMapper.selectPage(new Page<>(pageNo,size),wrapper); return new PageResult<>(result.getTotal(),result.getRecords().stream().map(this::dto).toList()); }
    public ActorAssetRespDTO asset(Long userId,Long assetId){return dto(require(userId,assetId));}
    public ActorAssetRespDTO upload(Long userId,String mediaType,String categoryCode,MultipartFile file){PrivateActorMediaStorage.StoredObjectRef object=storage.store(userId,mediaType,file);if(!"pdf".equals(mediaType))return createReadyAsset(userId,mediaType,categoryCode,object,file.getOriginalFilename(),file.getContentType(),file.getSize());ActorMediaAsset a=newAsset(userId,mediaType,categoryCode,object,file,"processing");assetMapper.insert(a);try{var pages=pdfProcessor.process(userId,file);for(int i=0;i<pages.size();i++){ActorMediaAssetPage page=new ActorMediaAssetPage();page.setAssetId(a.getAssetId());page.setPageNo(i+1);page.setImageObjectKey(pages.get(i).objectKey());page.setProcessStatus("ready");pageMapper.insert(page);}a.setPageCount(pages.size());a.setProcessStatus("ready");}catch(ActorPrivatePdfProcessor.PdfProcessingException error){a.setProcessStatus("failed");a.setFailureCode(error.code());a.setFailureMessage(error.getMessage());}assetMapper.updateById(a);return dto(a);}
    public ActorAssetRespDTO retryPdf(Long userId,Long failedAssetId,MultipartFile file){ActorMediaAsset failed=require(userId,failedAssetId);if(!"pdf".equals(failed.getMediaType())||!"failed".equals(failed.getProcessStatus()))throw ProfileDomainErrorCode.PROFILE_ASSET_NOT_READY.toException();return upload(userId,"pdf",failed.getCategoryCode(),file);}
    public ActorAssetRespDTO createReadyAsset(Long userId,String mediaType,String category,PrivateActorMediaStorage.StoredObjectRef object,String name,String mime,Long size){ ActorMediaAsset a=new ActorMediaAsset(); a.setUserId(userId);a.setMediaType(mediaType);a.setCategoryCode(category);a.setStorageProvider(object.storageProvider());a.setBucketCode(object.bucketCode());a.setObjectKey(object.objectKey());a.setThumbnailObjectKey(object.thumbnailObjectKey());a.setOriginalName(name);a.setMimeType(mime);a.setSizeBytes(size);a.setProcessStatus("ready");a.setSourceType("upload");assetMapper.insert(a);return dto(a); }
    public ActorAssetRespDTO update(Long userId,Long assetId,ActorAssetUpdateDTO request){ActorMediaAsset a=require(userId,assetId);a.setOriginalName(trim(request.getOriginalName()));a.setCategoryCode(trim(request.getCategoryCode()));assetMapper.updateById(a);return dto(a);}
    @Transactional(rollbackFor=Exception.class) public void setCurrentResume(Long userId,ActorCurrentResumeUpdateDTO request){ActorMediaAsset a=requireForUpdate(userId,request.getAssetId());if(!"pdf".equals(a.getMediaType())||!"ready".equals(a.getProcessStatus()))throw ProfileDomainErrorCode.PROFILE_ASSET_NOT_READY.toException();ActorProfile p=profileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>().eq(ActorProfile::getUserId,userId).last("limit 1"));if(p==null)throw ProfileDomainErrorCode.PROFILE_ASSET_NOT_FOUND.toException();p.setCurrentResumeAssetId(a.getAssetId());profileMapper.updateById(p);}
    @Transactional(rollbackFor=Exception.class) public void bindProfileAsset(Long userId,Long assetId,String usageCode,Integer sortNo){ActorMediaAsset a=requireForUpdate(userId,assetId);if(!"photo".equals(a.getMediaType())||!"ready".equals(a.getProcessStatus()))throw ProfileDomainErrorCode.PROFILE_ASSET_NOT_READY.toException();ActorProfile p=profileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>().eq(ActorProfile::getUserId,userId).last("limit 1"));if(p==null)throw ProfileDomainErrorCode.PROFILE_ASSET_NOT_FOUND.toException();ActorProfileAsset relation=new ActorProfileAsset();relation.setActorProfileId(p.getActorProfileId());relation.setAssetId(assetId);relation.setUsageCode(usageCode);relation.setSortNo(sortNo);profileAssetMapper.insert(relation);}
    @Transactional(rollbackFor = Exception.class)
    public void replaceWorkAssets(Long userId, Long experienceId, ActorWorkAssetsReplaceDTO request) {
        List<NormalizedWorkAssetBinding> desired = validateAndNormalizeBindings(request);
        ActorExperience work = experienceMapper.selectOwnedActiveByIdForUpdate(userId, experienceId);
        if (work == null
                || !Objects.equals(work.getUserId(), userId)
                || !Objects.equals(work.getExperienceId(), experienceId)
                || work.getActorProfileId() == null) {
            throw ProfileDomainErrorCode.PROFILE_WORK_IN_USE.toException();
        }
        ActorProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<ActorProfile>()
                .eq(ActorProfile::getActorProfileId, work.getActorProfileId())
                .eq(ActorProfile::getUserId, userId)
                .last("limit 1"));
        if (profile == null
                || !Objects.equals(profile.getActorProfileId(), work.getActorProfileId())
                || !Objects.equals(profile.getUserId(), userId)) {
            throw ProfileDomainErrorCode.PROFILE_WORK_IN_USE.toException();
        }

        validateAndLockAssets(userId, desired);
        List<NormalizedWorkAssetBinding> current = workAssetMapper.selectList(
                        new LambdaQueryWrapper<ActorWorkAsset>()
                                .eq(ActorWorkAsset::getExperienceId, experienceId)
                                .orderByAsc(ActorWorkAsset::getUsageCode)
                                .orderByAsc(ActorWorkAsset::getSortNo)
                                .orderByAsc(ActorWorkAsset::getAssetId))
                .stream()
                .map(relation -> new NormalizedWorkAssetBinding(
                        relation.getAssetId(), relation.getUsageCode(), relation.getSortNo()))
                .sorted(WORK_ASSET_ORDER)
                .toList();
        if (current.equals(desired)) {
            return;
        }

        workAssetMapper.deleteActiveByExperienceId(experienceId);
        for (NormalizedWorkAssetBinding binding : desired) {
            ActorWorkAsset relation = new ActorWorkAsset();
            relation.setExperienceId(experienceId);
            relation.setAssetId(binding.assetId());
            relation.setUsageCode(binding.usageCode());
            relation.setSortNo(binding.sortNo());
            workAssetMapper.insert(relation);
        }
        if (profileMapper.incrementWorkLibraryVersion(profile.getActorProfileId()) != 1) {
            throw new IllegalStateException("work library version increment failed");
        }
    }
    @Transactional(propagation=Propagation.MANDATORY, rollbackFor=Exception.class) public void requireOwnedReadyPhoto(Long userId,Long assetId){ ActorMediaAsset a=requireForUpdate(userId,assetId); if(!"photo".equals(a.getMediaType())||!"ready".equals(a.getProcessStatus())) throw ProfileDomainErrorCode.PROFILE_ASSET_NOT_READY.toException(); }
    public ActorAssetAccessUrlRespDTO issueOwnerAccessUrl(Long userId,Long assetId){ ActorMediaAsset a=require(userId,assetId); if(!"ready".equals(a.getProcessStatus())) throw ProfileDomainErrorCode.PROFILE_ASSET_NOT_READY.toException(); var signed=storage.issueAccessUrl(a.getBucketCode(),a.getObjectKey(),Duration.ofMinutes(10)); return new ActorAssetAccessUrlRespDTO(signed.accessUrl(),signed.expiresAt()); }
    @Transactional(rollbackFor=Exception.class) public void delete(Long userId,Long assetId){ ActorMediaAsset a=requireForUpdate(userId,assetId); if(profileMapper.selectCount(new LambdaQueryWrapper<ActorProfile>().eq(ActorProfile::getAvatarAssetId,assetId).or().eq(ActorProfile::getCurrentResumeAssetId,assetId))>0||profileAssetMapper.selectCount(new LambdaQueryWrapper<ActorProfileAsset>().eq(ActorProfileAsset::getAssetId,assetId))>0||workAssetMapper.selectCount(new LambdaQueryWrapper<ActorWorkAsset>().eq(ActorWorkAsset::getAssetId,assetId))>0||shareAssetMapper.selectCount(new LambdaQueryWrapper<ShareCardAsset>().eq(ShareCardAsset::getAssetId,assetId))>0) throw ProfileDomainErrorCode.PROFILE_ASSET_IN_USE.toException(); var pages=pageMapper.selectList(new LambdaQueryWrapper<ActorMediaAssetPage>().eq(ActorMediaAssetPage::getAssetId,assetId));for(ActorMediaAssetPage page:pages)storage.delete(a.getBucketCode(),page.getImageObjectKey());pageMapper.delete(new LambdaQueryWrapper<ActorMediaAssetPage>().eq(ActorMediaAssetPage::getAssetId,assetId));assetMapper.deleteById(assetId); storage.delete(a.getBucketCode(),a.getObjectKey()); }
    private ActorMediaAsset require(Long userId,Long id){ ActorMediaAsset a=assetMapper.selectOne(new LambdaQueryWrapper<ActorMediaAsset>().eq(ActorMediaAsset::getAssetId,id).eq(ActorMediaAsset::getUserId,userId).last("limit 1")); if(a==null) throw ProfileDomainErrorCode.PROFILE_ASSET_NOT_FOUND.toException(); return a; }
    private ActorMediaAsset requireForUpdate(Long userId, Long id) {
        List<ActorMediaAsset> assets = assetMapper.selectOwnedActiveByIdsForUpdate(userId, List.of(id));
        if (assets.isEmpty()) {
            throw ProfileDomainErrorCode.PROFILE_ASSET_NOT_FOUND.toException();
        }
        return assets.get(0);
    }
    private String trim(String value){return StringUtils.hasText(value)?value.trim():null;}
    private List<NormalizedWorkAssetBinding> validateAndNormalizeBindings(ActorWorkAssetsReplaceDTO request) {
        if (request == null || request.getBindings() == null) {
            throw parameterError("作品素材集合不能为空");
        }
        Set<Long> assetIds = new HashSet<>();
        Map<String, List<Integer>> sortNumbers = new HashMap<>();
        List<NormalizedWorkAssetBinding> normalized = new ArrayList<>();
        for (ActorAssetBindingDTO binding : request.getBindings()) {
            if (binding == null || binding.getAssetId() == null || binding.getAssetId() <= 0) {
                throw parameterError("作品素材 ID 无效");
            }
            if (!assetIds.add(binding.getAssetId())) {
                throw parameterError("作品素材不能重复");
            }
            String usageCode = binding.getUsageCode();
            if (!"still".equals(usageCode) && !"clip".equals(usageCode)) {
                throw parameterError("作品素材用途仅支持 still 或 clip");
            }
            Integer sortNo = binding.getSortNo();
            if (sortNo == null || sortNo <= 0) {
                throw parameterError("作品素材排序必须为正整数");
            }
            sortNumbers.computeIfAbsent(usageCode, ignored -> new ArrayList<>()).add(sortNo);
            normalized.add(new NormalizedWorkAssetBinding(binding.getAssetId(), usageCode, sortNo));
        }
        for (List<Integer> usageSortNumbers : sortNumbers.values()) {
            List<Integer> sorted = usageSortNumbers.stream().sorted().toList();
            for (int index = 0; index < sorted.size(); index++) {
                if (sorted.get(index) != index + 1) {
                    throw parameterError("同一用途的作品素材排序必须从 1 连续递增且不能重复");
                }
            }
        }
        return normalized.stream().sorted(WORK_ASSET_ORDER).toList();
    }
    private void validateAndLockAssets(Long userId, List<NormalizedWorkAssetBinding> bindings) {
        if (bindings.isEmpty()) {
            return;
        }
        List<Long> assetIds = bindings.stream()
                .map(NormalizedWorkAssetBinding::assetId)
                .sorted()
                .toList();
        Map<Long, ActorMediaAsset> assets = assetMapper
                .selectOwnedActiveByIdsForUpdate(userId, assetIds)
                .stream()
                .collect(Collectors.toMap(ActorMediaAsset::getAssetId, Function.identity(), (left, right) -> left));
        for (NormalizedWorkAssetBinding binding : bindings) {
            ActorMediaAsset asset = assets.get(binding.assetId());
            if (asset == null || !Objects.equals(asset.getUserId(), userId)) {
                throw ProfileDomainErrorCode.PROFILE_ASSET_NOT_FOUND.toException();
            }
            String requiredType = "still".equals(binding.usageCode()) ? "photo" : "video";
            if (!"ready".equals(asset.getProcessStatus()) || !requiredType.equals(asset.getMediaType())) {
                throw ProfileDomainErrorCode.PROFILE_ASSET_NOT_READY.toException();
            }
        }
    }
    private BizException parameterError(String message) {
        return new BizException(ResultCode.PARAM_ERROR.getCode(), message);
    }
    private static final Comparator<NormalizedWorkAssetBinding> WORK_ASSET_ORDER = Comparator
            .comparingInt((NormalizedWorkAssetBinding binding) -> "still".equals(binding.usageCode()) ? 0 : 1)
            .thenComparingInt(NormalizedWorkAssetBinding::sortNo)
            .thenComparingLong(NormalizedWorkAssetBinding::assetId);
    private record NormalizedWorkAssetBinding(Long assetId, String usageCode, Integer sortNo) {}
    private ActorMediaAsset newAsset(Long userId,String mediaType,String category,PrivateActorMediaStorage.StoredObjectRef object,MultipartFile file,String status){ActorMediaAsset a=new ActorMediaAsset();a.setUserId(userId);a.setMediaType(mediaType);a.setCategoryCode(category);a.setStorageProvider(object.storageProvider());a.setBucketCode(object.bucketCode());a.setObjectKey(object.objectKey());a.setThumbnailObjectKey(object.thumbnailObjectKey());a.setOriginalName(file.getOriginalFilename());a.setMimeType(file.getContentType());a.setSizeBytes(file.getSize());a.setProcessStatus(status);a.setSourceType("upload");return a;}
    private ActorAssetRespDTO dto(ActorMediaAsset a){ ActorAssetRespDTO d=new ActorAssetRespDTO();d.setAssetId(a.getAssetId());d.setMediaType(a.getMediaType());d.setCategoryCode(a.getCategoryCode());d.setOriginalName(a.getOriginalName());d.setMimeType(a.getMimeType());d.setSizeBytes(a.getSizeBytes());d.setPageCount(a.getPageCount());d.setProcessStatus(a.getProcessStatus());d.setFailureMessage(a.getFailureMessage());return d; }
}
