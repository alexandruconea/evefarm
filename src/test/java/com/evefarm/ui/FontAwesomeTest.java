package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontAwesomeTest {

    @Test
    void everyIconIsDrawnInItsColour() throws IOException {
        List<String> names;
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/icons/fontawesome"))) {
            names = files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".svg"))
                    .map(name -> name.substring(0, name.length() - 4)).sorted().toList();
        }
        assertFalse(names.isEmpty());
        Color red = new Color(0xDC2626);
        for (String name : names) {
            assertTrue(pixels(FontAwesome.icon(name, () -> red, 32), null, red) > 0,
                    name + " is not drawn in its colour");
        }
    }

    @Test
    void theTabsAndTheMenusHaveOneColourForAll() {
        assertTrue(pixels(FontAwesome.bar("star"), null, FontAwesome.neutral()) > 0);
        assertTrue(pixels(FontAwesome.menu("wallet"), null, FontAwesome.neutral()) > 0);
    }

    @Test
    void aDisabledButtonShowsItsIconGreyedOut() {
        Icon icon = FontAwesome.icon("plus", () -> Color.GREEN, 16);
        JButton button = new JButton();
        button.setEnabled(false);

        assertTrue(pixels(icon, null, Color.GREEN) > 0);
        assertEquals(0, pixels(icon, button, Color.GREEN));
        assertEquals(0, pixels(FontAwesome.disabled(icon), null, Color.GREEN));
    }

    @Test
    void everyIconOfTheButtonsAndMenusHasItsPicture() throws IllegalAccessException {
        for (Field field : Icons.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.get(null) instanceof Icon icon
                    && icon != Icons.BLANK) {
                assertTrue(pixels(icon, null, null) > 0, field.getName() + " draws nothing");
                assertEquals("", icon.toString(), "an icon in a table cell copies as no text");
            }
        }
    }

    private static int pixels(Icon icon, java.awt.Component component, Color colour) {
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        icon.paintIcon(component, g, 0, 0);
        g.dispose();
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color pixel = new Color(image.getRGB(x, y), true);
                boolean matches = colour == null || Math.abs(pixel.getRed() - colour.getRed()) <= 3
                        && Math.abs(pixel.getGreen() - colour.getGreen()) <= 3
                        && Math.abs(pixel.getBlue() - colour.getBlue()) <= 3;
                if (matches && pixel.getAlpha() >= 128) {
                    count++;
                }
            }
        }
        return count;
    }
}
