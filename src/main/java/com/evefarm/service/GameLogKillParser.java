package com.evefarm.service;

import com.evefarm.model.ParsedEncounter;
import com.evefarm.model.ParsedKill;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GameLogKillParser {

    public static final int PARSER_VERSION = 3;

    public static final Duration ENCOUNTER_GAP = Duration.ofSeconds(60);

    private static final Pattern FILE_NAME_CHARACTER_ID = Pattern.compile("^\\d{8}_\\d{6}_(\\d+)\\.txt$");

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");

    private static final Pattern LOG_LINE = Pattern.compile("^\\[ (.+?) \\] \\(([a-zA-Z]+)\\) (.*)$");

    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    private static final Pattern TO_FROM_TARGET =
            Pattern.compile("\\b(?:to|from)\\s+([A-Za-z0-9'’.\\- ]+?)(?=\\s+-|\\s+(?:to|from)\\b|\\s*$)");

    private static final Pattern MISSES_TARGET = Pattern.compile("^([A-Za-z0-9'’.\\- ]+?) misses you\\b");

    private static final Pattern DAMAGE_PREFIX = Pattern.compile("^(\\d+)\\s+(to|from)\\b");

    private static final Pattern BOUNTY_AMOUNT = Pattern.compile("^([0-9][0-9.,\\s\\u00a0]*)\\s*ISK\\b");
    private static final Pattern DECIMAL_TAIL = Pattern.compile("^(.*)[.,](\\d{2})$");

    private static final Pattern JUMP_LINE = Pattern.compile("^Jumping from \\S+ to (\\S+)$");
    private static final Pattern UNDOCK_LINE = Pattern.compile("to (\\S+) solar system\\.\\s*$");

    private static final Map<String, String> FACTION_KEYWORDS = new LinkedHashMap<>();

    static {
        FACTION_KEYWORDS.put("sansha", "Sansha's Nation");
        FACTION_KEYWORDS.put("guristas", "Guristas Pirates");
        FACTION_KEYWORDS.put("blood raider", "Blood Raiders");
        FACTION_KEYWORDS.put("angel", "Angel Cartel");
        FACTION_KEYWORDS.put("serpentis", "Serpentis");
        FACTION_KEYWORDS.put("rogue drone", "Rogue Drones");
        FACTION_KEYWORDS.put("mordu", "Mordu's Legion");
        FACTION_KEYWORDS.put("sleeper", "Sleepers");
        FACTION_KEYWORDS.put("triglavian", "Triglavian Collective");
        FACTION_KEYWORDS.put("edencom", "EDENCOM");
        FACTION_KEYWORDS.put("drifter", "Drifters");
    }

    public static final String OTHER_FACTION = "Other / Unrecognized";

    public static Optional<Long> extractCharacterId(String fileName) {
        Matcher m = FILE_NAME_CHARACTER_ID.matcher(fileName);
        if (!m.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(m.group(1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public record ParseResult(List<ParsedKill> kills, List<ParsedEncounter> encounters) {
    }

    public static List<ParsedKill> parseKills(List<String> lines) {
        return parse(lines).kills();
    }

    public static ParseResult parse(List<String> lines) {
        List<ParsedKill> kills = new ArrayList<>();
        List<ParsedEncounter> encounters = new ArrayList<>();
        String lastTargetName = null;
        String currentSystem = null;
        EncounterBuilder encounter = null;
        for (String line : lines) {
            Matcher lineMatcher = LOG_LINE.matcher(line);
            if (!lineMatcher.matches()) {
                continue;
            }
            String category = lineMatcher.group(2);
            String plainText = TAG.matcher(lineMatcher.group(3)).replaceAll("");

            if ("combat".equalsIgnoreCase(category)) {
                String target = extractTargetName(plainText);
                if (target == null) {
                    continue;
                }
                lastTargetName = target;
                Optional<Instant> at = parseTimestamp(lineMatcher.group(1));
                if (at.isPresent()) {
                    encounter = continueOrStart(encounter, encounters, at.get(), currentSystem);
                    encounter.recordCombat(target, at.get(), plainText);
                }
            } else if ("none".equalsIgnoreCase(category)) {
                String system = extractSystem(plainText);
                if (system != null) {
                    currentSystem = system;
                    encounter = close(encounter, encounters);
                }
            } else if ("bounty".equalsIgnoreCase(category) && lastTargetName != null) {
                Optional<Instant> killedAt = parseTimestamp(lineMatcher.group(1));
                if (killedAt.isPresent()) {
                    Double bounty = parseBountyAmount(plainText);
                    kills.add(new ParsedKill(killedAt.get(), lastTargetName, classifyFaction(lastTargetName),
                            currentSystem, bounty));
                    encounter = continueOrStart(encounter, encounters, killedAt.get(), currentSystem);
                    encounter.recordKill(lastTargetName, killedAt.get(), bounty);
                }
            }
        }
        close(encounter, encounters);
        return new ParseResult(kills, encounters);
    }

    private static EncounterBuilder continueOrStart(EncounterBuilder current, List<ParsedEncounter> finished,
                                                    Instant at, String system) {
        if (current != null && Objects.equals(current.solarSystem, system)
                && !at.isAfter(current.lastActivityAt.plus(ENCOUNTER_GAP))) {
            return current;
        }
        close(current, finished);
        return new EncounterBuilder(at, system);
    }

    private static EncounterBuilder close(EncounterBuilder current, List<ParsedEncounter> finished) {
        if (current != null) {
            finished.add(current.build());
        }
        return null;
    }

    static Double parseBountyAmount(String plainText) {
        Matcher amount = BOUNTY_AMOUNT.matcher(plainText.trim());
        if (!amount.find()) {
            return null;
        }
        String raw = amount.group(1).trim();
        String fraction = "0";
        Matcher decimal = DECIMAL_TAIL.matcher(raw);
        if (decimal.matches()) {
            raw = decimal.group(1);
            fraction = decimal.group(2);
        }
        String digits = raw.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : Double.parseDouble(digits + "." + fraction);
    }

    private static final class EncounterBuilder {
        private final Instant startedAt;
        private final String solarSystem;
        private final Map<String, NpcBuilder> npcs = new LinkedHashMap<>();
        private Instant lastActivityAt;

        EncounterBuilder(Instant startedAt, String solarSystem) {
            this.startedAt = startedAt;
            this.solarSystem = solarSystem;
            this.lastActivityAt = startedAt;
        }

        void recordCombat(String name, Instant at, String plainText) {
            NpcBuilder npc = touch(name, at);
            Matcher damage = DAMAGE_PREFIX.matcher(plainText.trim());
            if (damage.find()) {
                long amount = Long.parseLong(damage.group(1));
                if ("to".equals(damage.group(2))) {
                    npc.damageDealt += amount;
                } else {
                    npc.damageTaken += amount;
                }
            }
        }

        void recordKill(String name, Instant at, Double bounty) {
            NpcBuilder npc = touch(name, at);
            npc.kills++;
            npc.bounty += bounty == null ? 0 : bounty;
            npc.lastKillAt = at;
        }

        private NpcBuilder touch(String name, Instant at) {
            if (at.isAfter(lastActivityAt)) {
                lastActivityAt = at;
            }
            NpcBuilder npc = npcs.computeIfAbsent(name, n -> new NpcBuilder(at));
            if (at.isAfter(npc.lastSeenAt)) {
                npc.lastSeenAt = at;
            }
            return npc;
        }

        ParsedEncounter build() {
            List<ParsedEncounter.Npc> result = new ArrayList<>();
            for (Map.Entry<String, NpcBuilder> entry : npcs.entrySet()) {
                NpcBuilder npc = entry.getValue();
                result.add(new ParsedEncounter.Npc(entry.getKey(), npc.firstSeenAt, npc.lastSeenAt, npc.kills,
                        npc.bounty, npc.lastKillAt, npc.damageDealt, npc.damageTaken));
            }
            return new ParsedEncounter(startedAt, lastActivityAt, solarSystem, result);
        }
    }

    private static final class NpcBuilder {
        private final Instant firstSeenAt;
        private Instant lastSeenAt;
        private int kills;
        private double bounty;
        private Instant lastKillAt;
        private long damageDealt;
        private long damageTaken;

        NpcBuilder(Instant firstSeenAt) {
            this.firstSeenAt = firstSeenAt;
            this.lastSeenAt = firstSeenAt;
        }
    }

    private static String extractSystem(String plainText) {
        Matcher jump = JUMP_LINE.matcher(plainText.trim());
        if (jump.matches()) {
            return jump.group(1);
        }
        Matcher undock = UNDOCK_LINE.matcher(plainText.trim());
        if (undock.find()) {
            return undock.group(1);
        }
        return null;
    }

    private static String extractTargetName(String plainText) {
        String lastMatch = null;
        Matcher m = TO_FROM_TARGET.matcher(plainText);
        while (m.find()) {
            String candidate = m.group(1).trim();
            if (!candidate.isEmpty() && !candidate.equalsIgnoreCase("you")) {
                lastMatch = candidate;
            }
        }
        if (lastMatch != null) {
            return lastMatch;
        }
        Matcher missMatcher = MISSES_TARGET.matcher(plainText.trim());
        if (missMatcher.find()) {
            String candidate = missMatcher.group(1).trim();
            if (!candidate.isEmpty() && !candidate.equalsIgnoreCase("you")) {
                return candidate;
            }
        }
        return null;
    }

    public static String classifyFaction(String npcName) {
        String lower = npcName.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : FACTION_KEYWORDS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return lower.contains("officer") ? entry.getValue() + " Officers" : entry.getValue();
            }
        }
        return OTHER_FACTION;
    }

    private static Optional<Instant> parseTimestamp(String raw) {
        try {
            return Optional.of(LocalDateTime.parse(raw.trim(), TIMESTAMP_FORMAT).toInstant(ZoneOffset.UTC));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private GameLogKillParser() {
    }
}
