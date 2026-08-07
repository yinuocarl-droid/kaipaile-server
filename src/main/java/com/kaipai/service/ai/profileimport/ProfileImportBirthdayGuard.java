package com.kaipai.service.ai.profileimport;

import com.kaipai.model.actor.dto.ProfileDomainErrorCode;
import com.kaipai.model.actor.entity.ActorProfile;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;

public final class ProfileImportBirthdayGuard {
    private static final Set<String> BIRTHDAY_FIELDS = Set.of(
            "birth_year", "birth_month", "birth_day", "birth_precision");

    private ProfileImportBirthdayGuard() {
    }

    public static BirthdayTuple normalize(ActorProfile current, Map<String, String> finalValues) {
        if (finalValues.keySet().stream().noneMatch(BIRTHDAY_FIELDS::contains)) return null;

        Integer year = integer(finalValues, "birth_year", current == null ? null : current.getBirthYear());
        Integer month = integer(finalValues, "birth_month", current == null ? null : current.getBirthMonth());
        Integer day = integer(finalValues, "birth_day", current == null ? null : current.getBirthDayOfMonth());
        String precision = text(
                finalValues, "birth_precision", current == null ? null : current.getBirthPrecision());

        require(precision != null);
        try {
            require(year != null && year >= 1900 && year <= Year.now().getValue());
            return switch (precision) {
                case "year" -> {
                    require(!finalValues.containsKey("birth_month")
                            && !finalValues.containsKey("birth_day"));
                    yield new BirthdayTuple(year, null, null, precision);
                }
                case "month" -> {
                    require(month != null && !finalValues.containsKey("birth_day"));
                    YearMonth.of(year, month);
                    yield new BirthdayTuple(year, month, null, precision);
                }
                case "day" -> {
                    require(month != null && day != null);
                    LocalDate.of(year, month, day);
                    yield new BirthdayTuple(year, month, day, precision);
                }
                default -> throw conflict();
            };
        } catch (DateTimeException error) {
            throw conflict();
        }
    }

    private static Integer integer(
            Map<String, String> finalValues, String field, Integer currentValue) {
        if (!finalValues.containsKey(field)) return currentValue;
        try {
            return Integer.valueOf(finalValues.get(field).trim());
        } catch (RuntimeException error) {
            throw conflict();
        }
    }

    private static String text(
            Map<String, String> finalValues, String field, String currentValue) {
        if (!finalValues.containsKey(field)) return currentValue;
        String value = finalValues.get(field);
        return value == null ? null : value.trim();
    }

    private static void require(boolean condition) {
        if (!condition) throw conflict();
    }

    private static RuntimeException conflict() {
        return ProfileDomainErrorCode.PROFILE_IMPORT_APPLY_CONFLICT.toException();
    }

    public record BirthdayTuple(Integer year, Integer month, Integer day, String precision) {
    }
}
