package com.evefarm.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatTextBorder;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Rectangle2D;

public class UnderlineTextBorder extends FlatTextBorder {

    static final String UNDERLINE_COLOR = "TextComponent.underlineColor";

    @Override
    protected Paint getBorderColor(Component c) {
        return underlined(c) ? borderColor : super.getBorderColor(c);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        super.paintBorder(c, g, x, y, width, height);
        if (!underlined(c)) {
            return;
        }
        boolean focused = isFocused(c);
        Color color = focused ? focusedBorderColor : UIManager.getColor(UNDERLINE_COLOR);
        if (color == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            FlatUIUtils.setRenderingHints(g2);
            float inset = UIScale.scale((float) getFocusWidth(c));
            float arc = UIScale.scale((float) getArc(c));
            float w = width - inset * 2;
            float h = height - inset * 2;
            g2.clip(FlatUIUtils.createComponentRectangle(x + inset, y + inset, w, h, arc));
            float line = UIScale.scale(focused ? 2f : 1f);
            g2.setColor(color);
            g2.fill(new Rectangle2D.Float(x + inset, y + inset + h - line, w, line));
        } finally {
            g2.dispose();
        }
    }

    private boolean underlined(Component c) {
        return isEnabled(c) && !isCellEditor(c) && !(c instanceof JComponent component
                && component.getClientProperty(FlatClientProperties.OUTLINE) != null);
    }
}
