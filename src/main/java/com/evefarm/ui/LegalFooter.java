package com.evefarm.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

final class LegalFooter {

    static final String SIGNUP_URL =
            "https://www.eveonline.com/signup?invc=4f0a151b-7bdb-4115-85da-c1d675681afa";

    static final String SHORT_NOTICE = "EVE Farm code: MIT License  ·  EVE Online names, images and game data "
            + "© CCP hf.  ·  Not affiliated with Fenris Creations";

    static final String FULL_NOTICE = "EVE Farm's own code is released under the MIT License. The license doesn't "
            + "cover EVE Online's names, images or game data, which belong to their owners.\n\n"
            + "© 2014 CCP hf. All rights reserved. \"EVE\", \"EVE Online\", \"CCP\", and all related logos and "
            + "images are trademarks or registered trademarks of CCP hf.\n\n"
            + "EVE Farm is an independent, fan-made application that uses the official EVE Online APIs under the "
            + "EVE Developer License Agreement. It is not made, endorsed or supported by Fenris Creations (formerly "
            + "CCP Games), is not affiliated with it in any way, and Fenris Creations is not responsible for its "
            + "content or functioning.";

    private static final Logger LOG = Logger.getLogger(LegalFooter.class.getName());

    private LegalFooter() {
    }

    static JPanel create() {
        Color muted = UIManager.getColor("Label.disabledForeground");
        Color separator = UIManager.getColor("Separator.foreground");

        JLabel notice = new JLabel(SHORT_NOTICE);
        notice.putClientProperty("html.disable", Boolean.TRUE);
        notice.setFont(smaller(notice.getFont()));
        notice.setForeground(muted);

        JLabel signup = link("Free EVE account - supports EVE Farm", muted, () -> openSignupPage(notice));
        JLabel details = link("Legal notice", muted, () -> showFullNotice(notice));
        JLabel dot = new JLabel("·");
        dot.setFont(smaller(dot.getFont()));
        dot.setForeground(muted);
        JPanel links = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        links.setOpaque(false);
        links.add(signup);
        links.add(dot);
        links.add(details);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, separator != null ? separator : Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(3, 10, 4, 16)));
        footer.add(notice, BorderLayout.CENTER);
        footer.add(links, BorderLayout.EAST);
        return footer;
    }

    private static JLabel link(String text, Color muted, Runnable action) {
        JLabel link = new JLabel(text);
        link.setFont(smaller(link.getFont()));
        link.setForeground(muted);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                    action.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                Color accent = UIManager.getColor("Component.accentColor");
                link.setForeground(accent != null ? accent : muted);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                link.setForeground(muted);
            }
        });
        return link;
    }

    private static void openSignupPage(JLabel owner) {
        try {
            Desktop.getDesktop().browse(URI.create(SIGNUP_URL));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Couldn't open the EVE sign-up page", e);
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(owner),
                    "Couldn't open the browser:\n" + SIGNUP_URL, "Free EVE account", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static Font smaller(Font font) {
        return font.deriveFont(font.getSize2D() - 1f);
    }

    private static void showFullNotice(JLabel owner) {
        JTextArea text = new JTextArea(FULL_NOTICE);
        TextAreaStyler.scrollable(text);
        text.setColumns(52);
        JScrollPane scroll = new JScrollPane(text);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(520, 230));
        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(owner), scroll, "Legal Notice",
                JOptionPane.PLAIN_MESSAGE);
    }
}
