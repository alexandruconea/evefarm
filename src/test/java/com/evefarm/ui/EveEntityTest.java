package com.evefarm.ui;

import com.evefarm.model.JournalRow.Party;
import com.evefarm.service.ItemIconService.EveImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EveEntityTest {

    @Test
    void aJournalPartyShowsAPortraitOrALogoByWhatItIs() {
        assertEquals(EveImage.portrait(3014399L), EveEntity.party(new Party(3014399L, "Yasonar Anoh", "character"))
                .image());
        assertEquals(EveImage.logo(1000125L), EveEntity.party(new Party(1000125L, "CONCORD", "corporation"))
                .image());
        assertEquals(EveImage.allianceLogo(99003581L), EveEntity.party(new Party(99003581L, "Fraternity.",
                "alliance")).image());
        assertEquals(EveImage.logo(500001L), EveEntity.party(new Party(500001L, "Caldari State", "faction"))
                .image());
    }

    @Test
    void placesAndUnknownPartiesHaveNoPicture() {
        assertNull(EveEntity.party(new Party(60003760L, "Jita IV - Moon 4", "station")).image());
        assertNull(EveEntity.party(new Party(95465499L, "#95465499", null)).image());
        assertNull(EveEntity.party(new Party(null, null, null)).image());
        assertEquals("", EveEntity.party(new Party(null, null, null)).toString());
    }
}
