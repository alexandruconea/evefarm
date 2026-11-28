package com.evefarm.ui;

import com.evefarm.model.Decryptor;
import com.evefarm.model.InventionSource;
import com.evefarm.service.IndustryService.Option;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecryptorOptionsTableModelTest {

    private static final Decryptor ACCELERANT = new Decryptor(34201, "Accelerant Decryptor", 1.2, 2, 10, 1);
    private static final InventionSource WRECKED = new InventionSource(30562, "Wrecked Thruster Sections", true);

    private static Option option(InventionSource relic, Decryptor decryptor) {
        return new Option(relic, decryptor, null, null, null, 0, 0, null, 0, 0, null, 0);
    }

    @Test
    void tech3ShowsTheRelicAndTech2OnlyTheDecryptor() {
        assertEquals("No decryptor", DecryptorOptionsTableModel.inventedWith(option(null, null)));
        assertEquals("Accelerant Decryptor", DecryptorOptionsTableModel.inventedWith(option(null, ACCELERANT)));
        assertEquals("Wrecked Thruster Sections", DecryptorOptionsTableModel.inventedWith(option(WRECKED, null)));
        assertEquals("Wrecked Thruster Sections + Accelerant Decryptor",
                DecryptorOptionsTableModel.inventedWith(option(WRECKED, ACCELERANT)));
    }
}
