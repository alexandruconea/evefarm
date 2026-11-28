package com.evefarm.ui;

import com.evefarm.model.BlueprintRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.Text;

import java.util.List;

public final class BlueprintsTableModel extends ColumnTableModel<BlueprintRow> {

    private static final List<ColumnDef<BlueprintRow>> COLUMNS = List.of(
            new ColumnDef<>("owner", "Owner", EveEntity.class,
                    r -> EveEntity.owner(r.ownerName(), r.ownerId(), r.corporation()),
                    "The character or the corporation that owns the blueprint"),
            new ColumnDef<>("blueprint", "Blueprint", String.class, BlueprintRow::typeName,
                    "The blueprint's name"),
            new ColumnDef<>("kind", "Kind", String.class, BlueprintsTableModel::kind,
                    "Original or Copy. An original can run forever, a copy has a number of runs"),
            new ColumnDef<>("me", "ME", Integer.class, BlueprintRow::materialEfficiency,
                    "Material Efficiency: how much less material each run needs, in percent"),
            new ColumnDef<>("te", "TE", Integer.class, BlueprintRow::timeEfficiency,
                    "Time Efficiency: how much faster each run is, in percent"),
            new ColumnDef<>("runs", "Runs", String.class, BlueprintsTableModel::runs,
                    "How many runs a copy has left. Originals have no limit"),
            new ColumnDef<>("qty", "Qty", Long.class, BlueprintRow::count,
                    "How many of them sit together in one stack"),
            new ColumnDef<>("group", "Group", String.class, r -> r.groupName() == null ? "" : r.groupName(),
                    "The blueprint's group, such as Frigate Blueprint"),
            new ColumnDef<>("location", "Location", String.class, BlueprintRow::locationName,
                    "The station or structure the blueprint is in"),
            new ColumnDef<>("container", "Container", String.class,
                    r -> r.containerName() == null ? "" : r.containerName(),
                    "The container the blueprint is in, if any"),
            new ColumnDef<>("flag", "Flag", String.class, r -> Text.titleCase(r.locationFlag()),
                    "Where the blueprint sits: a hangar, a corporation hangar with its name, a cargo hold and so on"),
            new ColumnDef<>("itemId", "Item ID", Long.class, false, BlueprintRow::itemId,
                    "EVE's ID of this very blueprint"),
            new ColumnDef<>("typeId", "Type ID", Integer.class, false, BlueprintRow::typeId,
                    "EVE's ID of the blueprint type")
    );

    public BlueprintsTableModel() {
        super(COLUMNS);
    }

    static String kind(BlueprintRow row) {
        return row.copy() ? "Copy" : "Original";
    }

    static String runs(BlueprintRow row) {
        return row.copy() ? String.valueOf(row.runs()) : "";
    }
}
