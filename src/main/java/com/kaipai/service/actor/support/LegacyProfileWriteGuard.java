package com.kaipai.service.actor.support;

import com.kaipai.model.actor.dto.ActorPhotoCategoriesDTO;
import com.kaipai.model.actor.dto.ActorProfileSaveDTO;
import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Prevents the aggregate legacy endpoint from mutating collection-backed profile domains. */
@Component
public class LegacyProfileWriteGuard {

    public void assertCompatible(ActorProfileSaveDTO request) {
        if (request == null) {
            return;
        }
        if (hasValues(request.getWorkExperiences())
                || hasValues(request.getPhotos())
                || hasPhotoCategories(request.getPhotoCategories())
                || StringUtils.hasText(request.getAvatar())
                || StringUtils.hasText(request.getVideoUrl())
                || StringUtils.hasText(request.getResumePdfUrl())
                || StringUtils.hasText(request.getResumePdfName())
                || request.getResumePdfPageCount() != null
                || hasValues(request.getResumePdfPageImageUrls())) {
            throw ProfileDomainErrorCode.PROFILE_LEGACY_COLLECTION_WRITE_RETIRED.toException();
        }
    }

    private boolean hasPhotoCategories(ActorPhotoCategoriesDTO categories) {
        return categories != null
                && (hasValues(categories.getPortrait())
                || hasValues(categories.getLifestyle())
                || hasValues(categories.getProduction()));
    }

    private boolean hasValues(List<?> values) {
        return values != null && !values.isEmpty();
    }
}
