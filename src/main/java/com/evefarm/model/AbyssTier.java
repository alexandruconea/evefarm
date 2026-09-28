package com.evefarm.model;

public enum AbyssTier {
    TRANQUIL(0, "Tranquil"),
    CALM(1, "Calm"),
    AGITATED(2, "Agitated"),
    FIERCE(3, "Fierce"),
    RAGING(4, "Raging"),
    CHAOTIC(5, "Chaotic"),
    CATACLYSMIC(6, "Cataclysmic");

    private final int level;
    private final String filamentWord;

    AbyssTier(int level, String filamentWord) {
        this.level = level;
        this.filamentWord = filamentWord;
    }

    public int level() {
        return level;
    }

    public String filamentWord() {
        return filamentWord;
    }

    @Override
    public String toString() {
        return "T" + level + " " + filamentWord;
    }
}
