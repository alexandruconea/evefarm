package com.evefarm.ui;

import com.evefarm.model.AgentRow;
import com.evefarm.service.ItemIconService.EveImage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentsTableModelTest {

    @Test
    void theAgentCorporationAndFactionShowTheirPictures() {
        AgentsTableModel model = new AgentsTableModel();
        model.setRows(List.of(new AgentRow(3008416L, "Antaken Kamola", 1000035L, "Caldari Navy", 500001L,
                "Caldari State", "Security", "BasicAgent", 4, false, null, null, "Josameto", 0.6, null, null, null,
                null, null)));

        List<Object> values = List.of(model.getValueAt(0, 0), model.getValueAt(0, 1), model.getValueAt(0, 2));

        assertEquals(List.of("Antaken Kamola", "Caldari Navy", "Caldari State"),
                values.stream().map(String::valueOf).toList());
        assertEquals(List.of(EveImage.portrait(3008416L), EveImage.logo(1000035L), EveImage.logo(500001L)),
                values.stream().map(value -> ((EveEntity) value).image()).toList());
    }
}
