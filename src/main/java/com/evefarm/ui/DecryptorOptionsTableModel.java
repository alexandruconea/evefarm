package com.evefarm.ui;

import com.evefarm.service.IndustryService;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.IskFormatter;

import java.util.List;
import java.util.Locale;

public final class DecryptorOptionsTableModel extends ColumnTableModel<IndustryService.Option> {

    private static final List<ColumnDef<IndustryService.Option>> COLUMNS = List.of(
            new ColumnDef<>("decryptor", "Decryptor", String.class,
                    o -> o.decryptor() == null ? "None" : o.decryptor().name()),
            new ColumnDef<>("chance", "Chance", String.class,
                    o -> String.format(Locale.US, "%.1f%%", o.invention().chance() * 100)),
            new ColumnDef<>("runs", "Runs per BPC", Integer.class, o -> o.invention().runsPerCopy()),
            new ColumnDef<>("me", "ME", Integer.class, o -> o.invention().me()),
            new ColumnDef<>("te", "TE", Integer.class, o -> o.invention().te()),
            new ColumnDef<>("inventionPerRun", "Invention per Run", String.class,
                    o -> IskFormatter.format(o.invention().costPerRun())),
            new ColumnDef<>("unitCost", "Cost per Unit", String.class, o -> IskFormatter.format(o.unitCost())),
            new ColumnDef<>("unitProfit", "Profit per Unit", String.class, o -> IskFormatter.format(o.unitProfit())),
            new ColumnDef<>("profit", "Profit", String.class, o -> IskFormatter.format(o.profit())),
            new ColumnDef<>("iskPerHour", "ISK/h", String.class, o -> IskFormatter.format(o.iskPerHour()))
    );

    public DecryptorOptionsTableModel() {
        super(COLUMNS);
    }
}
