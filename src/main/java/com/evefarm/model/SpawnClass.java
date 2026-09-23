package com.evefarm.model;

public enum SpawnClass {
    OTHER("Other"),
    BELT("Belt rat"),
    COMMANDER("Commander"),
    OFFICER("Officer");

    private final String label;

    SpawnClass(String label) {
        this.label = label;
    }

    public static SpawnClass fromGroupName(String groupName) {
        if (groupName == null || !groupName.startsWith("Asteroid ")) {
            return OTHER;
        }
        if (groupName.contains(" Officer")) {
            return OFFICER;
        }
        if (groupName.contains(" Commander")) {
            return COMMANDER;
        }
        return BELT;
    }

    @Override
    public String toString() {
        return label;
    }
}
