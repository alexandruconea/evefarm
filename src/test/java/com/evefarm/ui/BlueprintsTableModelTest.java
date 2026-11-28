package com.evefarm.ui;

import com.evefarm.model.BlueprintRow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlueprintsTableModelTest {

    private static BlueprintRow blueprint(int quantity, int runs) {
        return new BlueprintRow(1, "Deep Core Mining [DCM]", 2, 3, "Rifter Blueprint", "Frigate Blueprint", quantity,
                10, 20, runs, "Jita", null, "Hangar 1", false);
    }

    @Test
    void copiesShowTheirRunsAndOriginalsDoNot() {
        assertEquals("Original", BlueprintsTableModel.kind(blueprint(-1, -1)));
        assertEquals("", BlueprintsTableModel.runs(blueprint(-1, -1)));
        assertEquals("Copy", BlueprintsTableModel.kind(blueprint(-2, 15)));
        assertEquals("15", BlueprintsTableModel.runs(blueprint(-2, 15)));
        assertEquals("Original", BlueprintsTableModel.kind(blueprint(4, -1)), "a stack of originals");
        assertEquals(4, blueprint(4, -1).count());
    }
}
