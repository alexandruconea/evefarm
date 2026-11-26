package com.evefarm.ui;

import com.evefarm.model.StandingRow;
import com.evefarm.service.ItemIconService.EveImage;
import com.evefarm.ui.EveEntity.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandingsTableModelTest {

    @Test
    void factionsAndCorporationsShowTheirLogo() {
        StandingRow faction = new StandingRow(1, "Pilot", 500001L, "faction", "Caldari State", 3.0, null, null, null,
                null, null, null, null);
        StandingRow corporation = new StandingRow(1, "Pilot", 1000035L, "npc_corp", "Caldari Navy", 4.1, null, null,
                500001L, "Caldari State", null, null, null);

        assertEquals(List.of(new EveEntity("Caldari State", Kind.FACTION, 500001L)),
                StandingsTableModel.entities(faction).filter(EveEntity::linked).toList());
        assertEquals(List.of(new EveEntity("Caldari Navy", Kind.CORPORATION, 1000035L),
                        new EveEntity("Caldari State", Kind.FACTION, 500001L)),
                StandingsTableModel.entities(corporation).filter(EveEntity::linked).toList());
    }

    @Test
    void anAgentShowsTheirPortraitAndTheLogosOfTheirCorporationAndFaction() {
        StandingRow agent = new StandingRow(1, "Pilot", 3008416L, "agent", "Antaken Kamola", 2.5, 1000035L,
                "Caldari Navy", 500001L, "Caldari State", 4, "Security", "Josameto");

        assertEquals(List.of(EveImage.portrait(3008416L), EveImage.logo(1000035L), EveImage.logo(500001L)),
                StandingsTableModel.entities(agent).map(EveEntity::image).toList());
    }
}
