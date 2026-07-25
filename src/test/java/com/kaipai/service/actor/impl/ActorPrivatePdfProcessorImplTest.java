package com.kaipai.service.actor.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.kaipai.service.actor.ActorPrivatePdfProcessor;
import com.kaipai.service.actor.PrivateActorMediaStorage;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ActorPrivatePdfProcessorImplTest {
    @Test void rendersEveryPageToPrivateGeneratedObjects() throws Exception {
        PrivateActorMediaStorage storage = mock(PrivateActorMediaStorage.class);
        when(storage.storeGenerated(eq(7L), eq("pdf-page"), any(byte[].class), eq("image/jpeg"), eq(".jpg")))
                .thenReturn(new PrivateActorMediaStorage.StoredObjectRef("cos", "private", "page.jpg", null));
        ActorPrivatePdfProcessorImpl processor = new ActorPrivatePdfProcessorImpl(storage);

        var result = processor.process(7L, pdf(2));

        assertEquals(2, result.size());
        verify(storage, times(2)).storeGenerated(eq(7L), eq("pdf-page"), any(byte[].class), eq("image/jpeg"), eq(".jpg"));
    }

    @Test void rejectsEmptyPdfWithStableFailureCode() throws Exception {
        ActorPrivatePdfProcessorImpl processor = new ActorPrivatePdfProcessorImpl(mock(PrivateActorMediaStorage.class));
        var error = assertThrows(ActorPrivatePdfProcessor.PdfProcessingException.class, () -> processor.process(7L, pdf(0)));
        assertEquals("PDF_PAGE_COUNT_INVALID", error.code());
    }

    @Test void cleansAlreadyStoredPagesWhenALaterPageUploadFails() throws Exception {
        PrivateActorMediaStorage storage = mock(PrivateActorMediaStorage.class);
        var firstPage = new PrivateActorMediaStorage.StoredObjectRef("cos", "private", "page-1.jpg", null);
        when(storage.storeGenerated(eq(7L), eq("pdf-page"), any(byte[].class), eq("image/jpeg"), eq(".jpg")))
                .thenReturn(firstPage)
                .thenThrow(new IllegalStateException("second page upload failed"));
        ActorPrivatePdfProcessorImpl processor = new ActorPrivatePdfProcessorImpl(storage);

        var error = assertThrows(ActorPrivatePdfProcessor.PdfProcessingException.class,
                () -> processor.process(7L, pdf(2)));

        assertEquals("PDF_RENDER_FAILED", error.code());
        verify(storage).delete("private", "page-1.jpg");
    }

    private MockMultipartFile pdf(int pageCount) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pageCount; i++) document.addPage(new PDPage());
            document.save(output);
            return new MockMultipartFile("file", "resume.pdf", "application/pdf", output.toByteArray());
        }
    }
}
