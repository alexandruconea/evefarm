package com.evefarm.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AggroLineParser {

    private static final Pattern LOG_LINE = Pattern.compile("^\\[ (.+?) \\] \\(([a-zA-Z]+)\\) (.*)$");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern DAMAGE_FROM = Pattern.compile("^\\d+ from (.+?) - \\S.*$");
    private static final Pattern MISSES_YOU = Pattern.compile("^(.+?) misses you completely\\b.*$");
    private static final Pattern WARP_ATTEMPT =
            Pattern.compile("^Warp (?:scramble|disruption) attempt from (.+?) to\\s*you!?$");
    private static final Pattern ENERGY_TAKEN = Pattern.compile("^\\d+ GJ energy (?:neutralized|drained) (.+?) - (.+)$");

    private AggroLineParser() {
    }

    static Optional<String> attacker(String line) {
        Matcher logLine = LOG_LINE.matcher(line.strip());
        if (!logLine.matches() || !"combat".equalsIgnoreCase(logLine.group(2))) {
            return Optional.empty();
        }
        String text = TAG.matcher(logLine.group(3)).replaceAll("").replaceAll("\\s+", " ").strip();
        for (Pattern pattern : new Pattern[]{DAMAGE_FROM, MISSES_YOU, WARP_ATTEMPT}) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.matches()) {
                return Optional.of(matcher.group(1).strip());
            }
        }
        Matcher energy = ENERGY_TAKEN.matcher(text);
        if (energy.matches() && energy.group(1).strip().equals(energy.group(2).strip())) {
            return Optional.of(energy.group(1).strip());
        }
        return Optional.empty();
    }
}
