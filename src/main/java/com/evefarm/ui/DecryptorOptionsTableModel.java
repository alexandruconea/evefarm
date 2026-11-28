package com.evefarm.ui;

import com.evefarm.service.IndustryService;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.ui.column.Isk;

import java.util.List;
import java.util.Locale;

public final class DecryptorOptionsTableModel extends ColumnTableModel<IndustryService.Option> {

    private static final List<ColumnDef<IndustryService.Option>> COLUMNS = List.of(
            new ColumnDef<>("decryptor", "Invented With", String.class, DecryptorOptionsTableModel::inventedWith,
                    "The decryptor used in the invention, and for Tech III the relic the copy is invented from"),
            new ColumnDef<>("chance", "Chance", String.class,
                    o -> String.format(Locale.US, "%.1f%%", o.invention().chance() * 100),
                    "The chance that an invention job succeeds, with your skills"),
            new ColumnDef<>("runs", "Runs per BPC", Integer.class, o -> o.invention().runsPerCopy(),
                    "How many runs each invented blueprint copy has"),
            new ColumnDef<>("me", "ME", Integer.class, o -> o.invention().me(),
                    "The material efficiency of the invented copy"),
            new ColumnDef<>("te", "TE", Integer.class, o -> o.invention().te(),
                    "The time efficiency of the invented copy"),
            new ColumnDef<>("inventionPerRun", "Invention per Run", Isk.class,
                    o -> Isk.of(o.invention().costPerRun()),
                    "What the invention costs for each run you build: datacores, the decryptor and the job fees, "
                            + "spread over the runs of a successful copy"),
            new ColumnDef<>("unitCost", "Cost per Unit", Isk.class, o -> Isk.of(o.unitCost()),
                    "What one unit costs you, the invention included"),
            new ColumnDef<>("unitProfit", "Profit per Unit", Isk.class, o -> Isk.signed(o.unitProfit()),
                    "The profit on one unit"),
            new ColumnDef<>("profit", "Profit", Isk.class, o -> Isk.signed(o.profit()),
                    "The profit on the whole build"),
            new ColumnDef<>("iskPerHour", "ISK/h", Isk.class, o -> Isk.of(o.iskPerHour()),
                    "The profit per hour of build time")
    );

    public DecryptorOptionsTableModel() {
        super(COLUMNS);
    }

    static String inventedWith(IndustryService.Option option) {
        String decryptor = option.decryptor() == null ? null : option.decryptor().name();
        if (option.relic() == null) {
            return decryptor == null ? "No decryptor" : decryptor;
        }
        return option.relic().name() + (decryptor == null ? "" : " + " + decryptor);
    }
}
