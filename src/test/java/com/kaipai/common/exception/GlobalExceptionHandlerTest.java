package com.kaipai.common.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kaipai.common.result.R;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    @Test
    void unexpectedExceptionShouldReturnGenericFailureWithUniqueCorrelationCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/api/verify/status");
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        R<Void> first;
        R<Void> second;
        try {
            first = handler.handleException(
                    new IllegalStateException("database credentials must not leak"), request);
            second = handler.handleException(
                    new IllegalStateException("another internal failure"), request);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertEquals(500, first.getCode());
        assertEquals("操作失败", first.getMessage());
        assertNull(first.getData());
        assertNotNull(first.getErrorCode());
        assertTrue(first.getErrorCode().matches("INTERNAL_ERROR_[0-9A-F]{32}"));
        assertFalse(first.getMessage().contains("database credentials"));
        assertNotEquals(first.getErrorCode(), second.getErrorCode());

        ILoggingEvent firstLog = appender.list.get(0);
        String logMessage = firstLog.getFormattedMessage();
        assertTrue(logMessage.contains(first.getErrorCode()));
        assertTrue(logMessage.contains("method=GET"));
        assertTrue(logMessage.contains("uri=/api/verify/status"));
        assertNotNull(firstLog.getThrowableProxy());
        assertEquals(IllegalStateException.class.getName(), firstLog.getThrowableProxy().getClassName());
    }
}
