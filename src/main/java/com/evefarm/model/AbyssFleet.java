package com.evefarm.model;

import java.util.Locale;
import java.util.Set;

public enum AbyssFleet {
    CRUISER(1, "1 Cruiser"),
    DESTROYERS(2, "2 Destroyers"),
    FRIGATES(3, "3 Frigates");

    private static final Set<String> FRIGATE_GROUPS =
            Set.of("interceptor", "covert ops", "stealth bomber", "electronic attack ship");
    private static final Set<String> CRUISER_GROUPS = Set.of("combat recon ship", "force recon ship", "logistics");

    private final int ships;
    private final String label;

    AbyssFleet(int ships, String label) {
        this.ships = ships;
        this.label = label;
    }

    public int ships() {
        return ships;
    }

    public static int shipsOf(AbyssFleet fleet) {
        return fleet == null ? 1 : fleet.ships;
    }

    public static AbyssFleet forShipGroup(String groupName) {
        if (groupName == null) {
            return null;
        }
        String group = groupName.toLowerCase(Locale.ROOT);
        if (group.contains("frigate") || FRIGATE_GROUPS.contains(group)) {
            return FRIGATES;
        }
        if (group.contains("destroyer") || group.equals("interdictor")) {
            return DESTROYERS;
        }
        if (group.contains("cruiser") || CRUISER_GROUPS.contains(group)) {
            return CRUISER;
        }
        return null;
    }

    @Override
    public String toString() {
        return label;
    }
}
