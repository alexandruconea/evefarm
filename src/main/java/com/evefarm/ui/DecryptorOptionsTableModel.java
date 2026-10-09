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
                    o -> o.decryptor() == null ? "None" : o.decryptor().name(),
                    "The decryptor used in the invention, or None"),
            new ColumnDef<>("chance", "Chance", String.class,
                    o -> String.format(Locale.US, "%.1f%%", o.invention().chance() * 100),
                    "The chance that an invention job succeeds, with your skills"),
            new ColumnDef<>("runs", "Runs per BPC", Integer.class, o -> o.invention().runsPerCopy(),
                    "How many runs each invented blueprint copy has"),
            new ColumnDef<>("me", "ME", Integer.class, o -> o.invention().me(),
                    "The material efficiency of the invented copy"),
            new ColumnDef<>("te", "TE", Integer.class, o -> o.invention().te(),
                    "The time efficiency of the invented copy"),
            new ColumnDef<>("inventionPerRun", "Invention per Run", String.class,
                    o -> IskFormatter.format(o.invention().costPerRun()),
                    "What the invention costs for each run you build: datacores, the decryptor and the job fees, "
                            + "spread over the runs of a successful copy"),
            new ColumnDef<>("unitCost", "Cost per Unit", String.class, o -> IskFormatter.format(o.unitCost()),
                    "What one unit costs you, the invention included"),
            new ColumnDef<>("unitProfit", "Profit per Unit", String.class, o -> IskFormatter.format(o.unitProfit()),
                    "The profit on one unit"),
            new ColumnDef<>("profit", "Profit", String.class, o -> IskFormatter.format(o.profit()),
                    "The profit on the whole build"),
            new ColumnDef<>("iskPerHour", "ISK/h", String.class, o -> IskFormatter.format(o.iskPerHour()),
                    "The profit per hour of build time")
    );

    public DecryptorOptionsTableModel() {
        super(COLUMNS);
    }
}
