package com.kaipai.service.actor;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ActorPrivatePdfProcessor {
    List<PrivateActorMediaStorage.StoredObjectRef> process(Long userId, MultipartFile file);

    class PdfProcessingException extends RuntimeException {
        private final String code;
        public PdfProcessingException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }
}
