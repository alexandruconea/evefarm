package com.evefarm.model;

public record Decryptor(int typeId, String name, double probabilityMultiplier, int meModifier, int teModifier,
                        int runsModifier) {
}
