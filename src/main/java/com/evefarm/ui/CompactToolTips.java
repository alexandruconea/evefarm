package com.evefarm.ui;

import com.evefarm.util.Text;

import javax.swing.JComponent;
import javax.swing.JToolTip;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import java.beans.PropertyChangeListener;

public final class CompactToolTips {

    private static final String UI_KEY = "ToolTipUI";
    private static final String TEXT_PROPERTY = "tiptext";
    private static final int LONG_TEXT = 60;
    private static final int WIDTH_PX = 260;
    private static final PropertyChangeListener COMPACT_TEXT = event -> {
        if (event.getSource() instanceof JToolTip tip && event.getNewValue() instanceof String text) {
            String compact = compact(text);
            if (!compact.equals(text)) {
                tip.setTipText(compact);
            }
        }
    };

    public static void install() {
        UIManager.put(UI_KEY, CompactToolTips.class.getName());
    }

    public static ComponentUI createUI(JComponent component) {
        component.removePropertyChangeListener(TEXT_PROPERTY, COMPACT_TEXT);
        component.addPropertyChangeListener(TEXT_PROPERTY, COMPACT_TEXT);
        return UIManager.getLookAndFeelDefaults().getUI(component);
    }

    static String compact(String text) {
        if (text.regionMatches(true, 0, "<html>", 0, 6) || text.length() <= LONG_TEXT && text.indexOf('\n') < 0) {
            return text;
        }
        return "<html><body style=\"width: " + WIDTH_PX + "px\">" + Text.escapeHtml(text).replace("\n", "<br>")
                + "</body></html>";
    }

    private CompactToolTips() {
    }
}
