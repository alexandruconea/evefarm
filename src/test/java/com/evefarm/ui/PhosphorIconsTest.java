package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhosphorIconsTest {

    @Test
    void everyIconIsDrawnInItsColourWithALightFillBehind() throws IOException {
        List<String> names;
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/icons/phosphor"))) {
            names = files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".svg"))
                    .map(name -> name.substring(0, name.length() - 4)).sorted().toList();
        }
        assertFalse(names.isEmpty());
        Color red = PhosphorIcons.Hue.RED.color();
        for (String name : names) {
            Icon icon = PhosphorIcons.icon(name, PhosphorIcons.Hue.RED, 32);
            BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            icon.paintIcon(null, g, 0, 0);
            g.dispose();
            int solid = 0;
            int tinted = 0;
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    Color pixel = new Color(image.getRGB(x, y), true);
                    boolean sameHue = Math.abs(pixel.getRed() - red.getRed()) <= 3
                            && Math.abs(pixel.getGreen() - red.getGreen()) <= 3
                            && Math.abs(pixel.getBlue() - red.getBlue()) <= 3;
                    if (sameHue && pixel.getAlpha() == 255) {
                        solid++;
                    } else if (sameHue && pixel.getAlpha() > 20 && pixel.getAlpha() < 90) {
                        tinted++;
                    }
                }
            }
            assertTrue(solid > 0, name + " has no outline in its colour");
            assertTrue(tinted > 0, name + " has no light fill");
        }
    }
}
