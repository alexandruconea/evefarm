package com.evefarm.model;

public record ItemType(int typeId, String typeName, boolean officer) {

    @Override
    public String toString() {
        return typeName;
    }
}
