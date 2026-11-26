package com.evefarm.ui;

import com.evefarm.model.MissionAgent;
import com.evefarm.service.ItemIconService.EveImage;
import com.evefarm.service.MissionIncome.AgentTotals;
import com.evefarm.ui.EveEntity.Kind;
import com.evefarm.ui.MissionAgentsTableModel.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionAgentsTableModelTest {

    private static final MissionAgent YASONAR = new MissionAgent(3014399L, "Yasonar Anoh", 1000035L, "Caldari Navy",
            500001L, "Caldari State");
    private static final Row ROW = new Row(new AgentTotals("Yasonar Anoh", 3, 4_000_000, 3_000_000, 9_900_000),
            YASONAR);

    @Test
    void eachAgentLinksToItselfItsCorporationAndItsFaction() {
        assertEquals(new EveEntity("Yasonar Anoh", Kind.AGENT, 3014399L), ROW.agentLink());
        assertEquals(new EveEntity("Caldari Navy", Kind.CORPORATION, 1000035L), ROW.corporationLink());
        assertEquals(new EveEntity("Caldari State", Kind.FACTION, 500001L), ROW.factionLink());
        assertEquals(EveImage.portrait(3014399L), ROW.agentLink().image());
        assertEquals(EveImage.logo(1000035L), ROW.corporationLink().image());
        assertEquals(EveImage.logo(500001L), ROW.factionLink().image());
    }

    @Test
    void bountiesFarFromATurnInLinkNowhere() {
        Row row = new Row(new AgentTotals(null, 0, 0, 0, 6_000_000), null);

        assertEquals(MissionAgentsTableModel.NO_TURN_IN, row.agentLink().toString());
        assertTrue(row.links().noneMatch(EveEntity::linked));
        assertNull(row.agentLink().image());
    }

    @Test
    void anAgentIsLookedForUnderItsCorporationOrElseItsFaction() {
        assertEquals(new StandingTarget(3014399L, List.of(1000035L, 500001L), "Malpais Legate"),
                StandingTarget.of(ROW.agentLink(), YASONAR, "Malpais Legate"));
        assertEquals(new StandingTarget(null, List.of(1000035L, 500001L), null),
                StandingTarget.of(ROW.corporationLink(), YASONAR, null));
        assertEquals(new StandingTarget(null, List.of(500001L), null),
                StandingTarget.of(ROW.factionLink(), YASONAR, null));
    }
}
