package com.evefarm.ui;

import com.evefarm.model.JournalRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.ui.column.Isk;
import com.evefarm.util.DateUtil;
import com.evefarm.util.Text;

import java.util.List;

public final class JournalTableModel extends ColumnTableModel<JournalRow> {

    private static final List<ColumnDef<JournalRow>> COLUMNS = List.of(
            new ColumnDef<>("character", "Character", EveEntity.class,
                    r -> EveEntity.character(r.characterName(), r.characterId()),
                    "The character whose wallet it is"),
            new ColumnDef<>("date", "Date", String.class, r -> DateUtil.formatIsoInstant(r.date()),
                    "When it happened, in your local time"),
            new ColumnDef<>("type", "Type", String.class, r -> Text.titleCase(r.refType()),
                    "The kind of entry, such as Bounty Prizes or Market Transaction"),
            new ColumnDef<>("amount", "Amount", Isk.class, r -> Isk.signed(r.amount()),
                    "The ISK that came in (positive) or went out (negative)"),
            new ColumnDef<>("balance", "Balance", Isk.class, r -> Isk.of(r.balance()),
                    "The wallet balance after this entry"),
            new ColumnDef<>("description", "Description", String.class, r -> nullToEmpty(r.description()),
                    "EVE's description of the entry"),
            new ColumnDef<>("firstParty", "First Party", EveEntity.class, r -> EveEntity.party(r.firstParty()),
                    "The first side of the entry, usually the one who paid"),
            new ColumnDef<>("secondParty", "Second Party", EveEntity.class, r -> EveEntity.party(r.secondParty()),
                    "The other side of the entry, usually the one who got the ISK"),
            new ColumnDef<>("characterId", "Character ID", Long.class, false, JournalRow::characterId,
                    "EVE's ID of the character")
    );

    public JournalTableModel() {
        super(COLUMNS);
    }
}
