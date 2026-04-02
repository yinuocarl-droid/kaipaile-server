package com.kaipai.common.config;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

import java.beans.PropertyEditorSupport;

@ControllerAdvice
public class OptionalQueryNumberBindingAdvice {

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Integer.class, new OptionalIntegerEditor());
        binder.registerCustomEditor(Long.class, new OptionalLongEditor());
    }

    private static boolean isNullLike(String text) {
        if (text == null) {
            return true;
        }
        String normalized = text.trim();
        return normalized.isEmpty()
                || "undefined".equalsIgnoreCase(normalized)
                || "null".equalsIgnoreCase(normalized);
    }

    private static final class OptionalIntegerEditor extends PropertyEditorSupport {
        @Override
        public void setAsText(String text) {
            if (isNullLike(text)) {
                setValue(null);
                return;
            }
            setValue(Integer.valueOf(text.trim()));
        }
    }

    private static final class OptionalLongEditor extends PropertyEditorSupport {
        @Override
        public void setAsText(String text) {
            if (isNullLike(text)) {
                setValue(null);
                return;
            }
            setValue(Long.valueOf(text.trim()));
        }
    }
}
