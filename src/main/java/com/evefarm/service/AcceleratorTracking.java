package com.evefarm.service;

import com.evefarm.model.Accelerator;
import com.evefarm.model.CharacterAccelerator;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AcceleratorTracking {

    public static final int BIOLOGY_SKILL_ID = 3405;
    private static final double BIOLOGY_BONUS_PER_LEVEL = 0.2;
    private static final Duration GRACE = Duration.ofHours(1);
    private static final Pattern TIME_LEFT = Pattern.compile(
            "^(?:(\\d+)\\s*d)?\\s*(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*m)?$");
    private static final Pattern CLOCK = Pattern.compile("^(\\d+):(\\d{1,2})(?::(\\d{1,2}))?$");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static Optional<Accelerator> likelyAccelerator(List<Accelerator> catalog, int bonus) {
        return catalog.stream()
                .filter(accelerator -> accelerator.bonus() == bonus && accelerator.durationHours() > 0)
                .max(Comparator.comparingInt(Accelerator::typeId));
    }

    public static Duration duration(Accelerator accelerator, int biologyLevel) {
        double hours = accelerator.durationHours() * (1 + BIOLOGY_BONUS_PER_LEVEL * Math.max(0, biologyLevel));
        return Duration.ofMinutes(Math.round(hours * 60));
    }

    public static CharacterAccelerator track(CharacterAccelerator existing, long characterId, int bonus,
                                             Accelerator accelerator, int biologyLevel, Instant now) {
        if (bonus <= 0) {
            return null;
        }
        if (existing != null && existing.bonus() == bonus
                && (existing.endsAt() == null || now.isBefore(existing.endsAt().plus(GRACE)))) {
            if (existing.endsAt() == null && accelerator != null) {
                return new CharacterAccelerator(characterId, accelerator.typeId(), accelerator.name(), bonus,
                        existing.firstSeen(), existing.firstSeen().plus(duration(accelerator, biologyLevel)), false);
            }
            return existing;
        }
        if (accelerator == null) {
            return new CharacterAccelerator(characterId, null, null, bonus, now, null, false);
        }
        return new CharacterAccelerator(characterId, accelerator.typeId(), accelerator.name(), bonus, now,
                now.plus(duration(accelerator, biologyLevel)), false);
    }

    public static Instant parseEnd(String text, Instant now, ZoneId zone) {
        String value = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return parse(value, now, zone);
        } catch (ArithmeticException | DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private static Instant parse(String value, Instant now, ZoneId zone) {
        Matcher clock = CLOCK.matcher(value);
        if (clock.matches()) {
            return now.plus(Duration.ofHours(number(clock.group(1))).plusMinutes(number(clock.group(2)))
                    .plusSeconds(number(clock.group(3))));
        }
        Matcher matcher = TIME_LEFT.matcher(value);
        if (matcher.matches() && (matcher.group(1) != null || matcher.group(2) != null || matcher.group(3) != null)) {
            return now.plus(Duration.ofDays(number(matcher.group(1))).plusHours(number(matcher.group(2)))
                    .plusMinutes(number(matcher.group(3))));
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME).atZone(zone).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(value).atStartOfDay(zone).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private static long number(String group) {
        return group == null ? 0 : Long.parseLong(group);
    }

    private AcceleratorTracking() {
    }
}
