package com.evefarm.ui;

import com.evefarm.service.ItemIconService;
import com.evefarm.service.ItemIconService.EveImage;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class IconRowTest {

    @Test
    void iconsSitSideBySideAsTallAsTheTallest() {
        Icon star = new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
        Icon portrait = new ImageIcon(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB));

        Icon row = IconRow.of(star, null, portrait);

        assertEquals(16 + 4 + 32, row.getIconWidth());
        assertEquals(32, row.getIconHeight());
        assertSame(star, IconRow.of(null, star));
        assertNull(IconRow.of(null, null));
    }

    @Test
    void aPortraitNotYetLoadedKeepsItsSize() {
        ItemIconService icons = new ItemIconService();

        Icon live = icons.liveImage(EveImage.portrait(2124165849L), 32);

        assertEquals(32, live.getIconWidth());
        assertEquals(32, icons.imageOrPlaceholder(EveImage.portrait(2124165849L), 32).getIconWidth());
        assertEquals(ItemIconService.RENDER_SIZE, icons.imageOrPlaceholder(null).getIconHeight());
    }
}
