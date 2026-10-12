package com.evefarm.ui;

import com.evefarm.model.LpGain;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;

import java.util.List;
import java.util.Locale;

public final class LpGainsTableModel extends ColumnTableModel<LpGain> {

    private static final List<ColumnDef<LpGain>> COLUMNS = List.of(
            new ColumnDef<>("seenAt", "Seen At", String.class, r -> DateUtil.format(r.seenAt()),
                    "When EVE Farm read the LP and saw that it had gone up, in your local time"),
            new ColumnDef<>("character", "Character", String.class, LpGain::characterName,
                    "The character who earned the LP"),
            new ColumnDef<>("corporation", "Corporation", String.class, LpGain::corporationName,
                    "The corporation the LP is with"),
            new ColumnDef<>("lp", "LP Earned", String.class, r -> String.format(Locale.US, "%,d", r.points()),
                    "How much the LP went up since EVE Farm read it before"),
            new ColumnDef<>("since", "Since", String.class, r -> DateUtil.format(r.since()),
                    "When EVE Farm read the LP before: it was earned between this time and Seen At. Empty when "
                            + "that isn't known")
    );

    public LpGainsTableModel() {
        super(COLUMNS);
    }
}
