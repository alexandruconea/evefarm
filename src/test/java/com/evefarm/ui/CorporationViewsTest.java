package com.evefarm.ui;

import com.evefarm.model.Corporation;
import com.evefarm.model.CorporationAccess;
import com.evefarm.model.ValueSummary;
import com.evefarm.service.CorporationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorporationViewsTest {

    private static final Corporation DCM = new Corporation(98_000_001L, "Deep Core Mining", "DCM");

    @Test
    void theCharactersListSaysWhatEachCanRead() {
        CorporationAccess director = new CorporationAccess(1, DCM.corporationId(), List.of("Director", "Trader"),
                Instant.EPOCH);
        CorporationAccess accountant = new CorporationAccess(2, DCM.corporationId(),
                List.of("Junior_Accountant", "Hangar_Take_1"), Instant.EPOCH);

        assertEquals("Not asked", CharactersDialog.corporationText(false, null, null));
        assertEquals("Run Update > Corporation", CharactersDialog.corporationText(true, null, null));
        assertEquals("Deep Core Mining [DCM]  ·  Director", CharactersDialog.corporationText(true, director, DCM));
        assertEquals("Deep Core Mining [DCM]  ·  Junior Accountant",
                CharactersDialog.corporationText(true, accountant, DCM));
        assertEquals("Corporation #98000001  ·  Contracts only", CharactersDialog.corporationText(
                true, new CorporationAccess(3, DCM.corporationId(), List.of(), Instant.EPOCH), null));
    }

    @Test
    void theGrandTotalCanAddTheCorporations() {
        ValueSummary personal = new ValueSummary(100, 40, 50, 10, null, "Rifter", 1, null, 0, null, 0);
        CorporationService.Card card = new CorporationService.Card(DCM,
                new ValueSummary(30, 20, 10, 0, null, null, 0, null, 0, null, 0), List.of());

        ValueSummary total = ValuesPanel.withCorporations(personal, List.of(card));

        assertEquals(130, total.total(), 1e-9);
        assertEquals(60, total.walletBalance(), 1e-9);
        assertEquals(60, total.assetsValue(), 1e-9);
        assertEquals(10, total.sellOrdersValue(), 1e-9);
        assertEquals("Rifter", total.bestAssetName());
    }
}
