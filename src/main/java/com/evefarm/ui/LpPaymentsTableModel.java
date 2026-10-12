package com.evefarm.ui;

import com.evefarm.service.LpPurchaseService.PendingPayment;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;

import java.util.List;

public final class LpPaymentsTableModel extends ColumnTableModel<PendingPayment> {

    private static final List<ColumnDef<PendingPayment>> COLUMNS = List.of(
            new ColumnDef<>("date", "Paid On", String.class, r -> DateUtil.format(r.payment().date()),
                    "When the character paid the LP store, in your local time"),
            new ColumnDef<>("character", "Character", String.class, PendingPayment::characterName,
                    "The character who paid"),
            new ColumnDef<>("corporation", "Corporation", String.class, PendingPayment::corporationName,
                    "The corporation whose LP store was paid"),
            new ColumnDef<>("amount", "ISK Paid", String.class, r -> IskFormatter.format(r.payment().amount()),
                    "The ISK paid, from your wallet journal"),
            new ColumnDef<>("match", "Matching Offers", String.class, LpPaymentsTableModel::match,
                    "The offers of that store whose ISK cost, times a whole number, makes the amount paid. The "
                            + "first is the one that best fits what you listed, sold and have of each item")
    );

    public LpPaymentsTableModel() {
        super(COLUMNS);
    }

    private static String match(PendingPayment payment) {
        return switch (payment.candidates().size()) {
            case 0 -> "No offer matches";
            case 1 -> payment.candidates().getFirst().label();
            default -> payment.candidates().getFirst().label() + " or " + (payment.candidates().size() - 1)
                    + " more";
        };
    }
}
