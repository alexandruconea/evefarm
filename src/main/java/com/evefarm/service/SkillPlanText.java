package com.evefarm.service;

import com.evefarm.model.SkillInfo;
import com.evefarm.model.SkillPlanEntry;
import com.evefarm.model.SkillRequirement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SkillPlanText {

    private static final Pattern SKILL_LINE = Pattern.compile("^(.+?)[\\s\\t]+(1|2|3|4|5|I|II|III|IV|V)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FIT_HEADER = Pattern.compile("^\\[([^,\\]]+),[^\\]]*]$");
    private static final Pattern QUANTITY = Pattern.compile("\\s+x\\d+$");
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    public record ParsedSkills(List<SkillRequirement> skills, List<String> unknownLines) {
    }

    public static String export(List<SkillPlanEntry> entries, Map<Integer, SkillInfo> catalog) {
        StringBuilder text = new StringBuilder();
        for (SkillPlanEntry entry : entries) {
            SkillInfo skill = catalog.get(entry.skillId());
            if (skill != null) {
                text.append(skill.name()).append(' ').append(entry.level()).append('\n');
            }
        }
        return text.toString();
    }

    public static String roman(int level) {
        return level >= 1 && level < ROMAN.length ? ROMAN[level] : String.valueOf(level);
    }

    public static ParsedSkills parseSkills(String text, Map<Integer, SkillInfo> catalog) {
        Map<String, Integer> idsByName = new HashMap<>();
        for (SkillInfo skill : catalog.values()) {
            idsByName.put(skill.name().toLowerCase(Locale.ROOT), skill.skillId());
        }
        List<SkillRequirement> skills = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher matcher = SKILL_LINE.matcher(line);
            Integer skillId = matcher.matches()
                    ? idsByName.get(matcher.group(1).strip().toLowerCase(Locale.ROOT)) : null;
            if (skillId == null) {
                unknown.add(line);
            } else {
                skills.add(new SkillRequirement(skillId, level(matcher.group(2))));
            }
        }
        return new ParsedSkills(skills, unknown);
    }

    static int level(String text) {
        for (int level = 1; level < ROMAN.length; level++) {
            if (ROMAN[level].equalsIgnoreCase(text) || String.valueOf(level).equals(text)) {
                return level;
            }
        }
        return 0;
    }

    public static boolean isFit(String text) {
        return text.lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst()
                .map(line -> FIT_HEADER.matcher(line).matches()).orElse(false);
    }

    public static List<String> fitItemNames(String text) {
        Set<String> names = new LinkedHashSet<>();
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher header = FIT_HEADER.matcher(line);
            if (header.matches()) {
                names.add(header.group(1).strip());
                continue;
            }
            if (line.startsWith("[")) {
                continue;
            }
            for (String part : line.split(",")) {
                String name = QUANTITY.matcher(part.replace("/offline", "").strip()).replaceAll("").strip();
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return List.copyOf(names);
    }

    private SkillPlanText() {
    }
}
