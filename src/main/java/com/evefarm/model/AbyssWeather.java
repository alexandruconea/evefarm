package com.evefarm.model;

public enum AbyssWeather {
    DARK("Dark"),
    ELECTRICAL("Electrical"),
    EXOTIC("Exotic"),
    FIRESTORM("Firestorm"),
    GAMMA("Gamma");

    private final String label;

    AbyssWeather(String label) {
        this.label = label;
    }

    public static String filamentName(AbyssTier tier, AbyssWeather weather) {
        return tier.filamentWord() + " " + weather.label + " Filament";
    }

    @Override
    public String toString() {
        return label;
    }
}
