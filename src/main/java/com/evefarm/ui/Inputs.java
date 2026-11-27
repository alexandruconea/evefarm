package com.evefarm.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Point;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

public final class Inputs {

    public static final String AMOUNT_PATTERN = "#,##0.##";
    private static final Set<String> ARROW_BUTTONS = Set.of("Spinner.nextButton", "Spinner.previousButton");
    private static final String CHECK = "evefarm.inputCheck";
    private static final String WATCHED = "evefarm.inputWatched";
    private static final String BALLOON = "evefarm.inputBalloon";
    private static final int SEARCH_ICON_SIZE = 14;
    private static final int BALLOON_MILLIS = 6000;

    private record Balloon(Popup popup, Timer timer) {
    }

    public static boolean embedsIcons() {
        return UIManager.getLookAndFeel() instanceof FlatLaf;
    }

    public static boolean search(JTextField field, String placeholder) {
        hint(field, placeholder);
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                FontAwesome.icon("magnifying-glass", UiColors::muted, SEARCH_ICON_SIZE));
        return embedsIcons();
    }

    public static void hint(JTextField field, String placeholder) {
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
    }

    public static void unit(JTextField field, String unit) {
        field.setHorizontalAlignment(SwingConstants.RIGHT);
        if (embedsIcons()) {
            JLabel label = UiColors.mutedLabel(unit);
            label.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
            field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, label);
        }
    }

    public static void amount(JSpinner spinner, String unit) {
        for (Component child : spinner.getComponents()) {
            if (child.getName() != null && ARROW_BUTTONS.contains(child.getName())) {
                spinner.remove(child);
            }
        }
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, AMOUNT_PATTERN);
        DecimalFormat format = editor.getFormat();
        format.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
        spinner.setEditor(editor);
        JFormattedTextField text = editor.getTextField();
        text.setValue(spinner.getValue());
        if (unit == null) {
            text.setHorizontalAlignment(SwingConstants.RIGHT);
        } else {
            unit(text, unit);
        }
    }

    public static void check(JTextComponent field, Predicate<String> valid) {
        field.putClientProperty(CHECK, valid);
        watch(field);
        recheck(field);
    }

    public static void reject(JTextComponent field, String message) {
        watch(field);
        outline(field, true);
        field.requestFocusInWindow();
        showBalloon(field, message);
    }

    static boolean isMarked(JComponent field) {
        return FlatClientProperties.OUTLINE_ERROR.equals(field.getClientProperty(FlatClientProperties.OUTLINE));
    }

    private static void outline(JComponent field, boolean invalid) {
        field.putClientProperty(FlatClientProperties.OUTLINE, invalid ? FlatClientProperties.OUTLINE_ERROR : null);
    }

    @SuppressWarnings("unchecked")
    private static void recheck(JTextComponent field) {
        String text = field.getText();
        outline(field, field.getClientProperty(CHECK) instanceof Predicate<?> check && !text.isBlank()
                && !((Predicate<String>) check).test(text));
    }

    private static void watch(JTextComponent field) {
        if (field.getClientProperty(WATCHED) != null) {
            return;
        }
        field.putClientProperty(WATCHED, Boolean.TRUE);
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                edited(field);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                edited(field);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                edited(field);
            }
        });
        field.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
                hideBalloon(field);
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
                hideBalloon(field);
            }
        });
    }

    private static void edited(JTextComponent field) {
        recheck(field);
        hideBalloon(field);
    }

    private static void showBalloon(JComponent field, String message) {
        hideBalloon(field);
        if (!field.isShowing()) {
            return;
        }
        JToolTip tip = field.createToolTip();
        tip.setTipText(message);
        Point at = field.getLocationOnScreen();
        Popup popup = PopupFactory.getSharedInstance().getPopup(field, tip, at.x, at.y + field.getHeight() + 2);
        Timer timer = new Timer(BALLOON_MILLIS, e -> hideBalloon(field));
        timer.setRepeats(false);
        field.putClientProperty(BALLOON, new Balloon(popup, timer));
        popup.show();
        timer.start();
    }

    private static void hideBalloon(JComponent field) {
        if (field.getClientProperty(BALLOON) instanceof Balloon balloon) {
            field.putClientProperty(BALLOON, null);
            balloon.timer().stop();
            balloon.popup().hide();
        }
    }

    private Inputs() {
    }
}
