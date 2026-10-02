package com.evefarm.model;

import java.util.List;

public record CharacterAttributes(int charisma, int intelligence, int memory, int perception, int willpower) {

    public static final String CHARISMA = "Charisma";
    public static final String INTELLIGENCE = "Intelligence";
    public static final String MEMORY = "Memory";
    public static final String PERCEPTION = "Perception";
    public static final String WILLPOWER = "Willpower";
    public static final List<String> NAMES = List.of(CHARISMA, INTELLIGENCE, MEMORY, PERCEPTION, WILLPOWER);
    public static final CharacterAttributes NONE = new CharacterAttributes(0, 0, 0, 0, 0);

    public static CharacterAttributes all(int value) {
        return new CharacterAttributes(value, value, value, value, value);
    }

    public int get(String attribute) {
        return switch (attribute) {
            case CHARISMA -> charisma;
            case INTELLIGENCE -> intelligence;
            case MEMORY -> memory;
            case PERCEPTION -> perception;
            case WILLPOWER -> willpower;
            default -> 0;
        };
    }

    public int total() {
        return charisma + intelligence + memory + perception + willpower;
    }

    public CharacterAttributes plus(CharacterAttributes other) {
        return new CharacterAttributes(charisma + other.charisma, intelligence + other.intelligence,
                memory + other.memory, perception + other.perception, willpower + other.willpower);
    }

    public CharacterAttributes minus(CharacterAttributes other) {
        return new CharacterAttributes(charisma - other.charisma, intelligence - other.intelligence,
                memory - other.memory, perception - other.perception, willpower - other.willpower);
    }
}
