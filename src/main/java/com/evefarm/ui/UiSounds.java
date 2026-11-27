package com.evefarm.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class UiSounds {

    public static final int DEFAULT_VOLUME = 40;
    static final String SOUND_TARGET = "evefarm.uiSound";
    static final float SAMPLE_RATE = 44_100f;
    private static final int HOVER_GAP_MILLIS = 40;
    private static final double ATTACK_MILLIS = 0.8;

    private static final Logger LOG = Logger.getLogger(UiSounds.class.getName());
    private static final ExecutorService AUDIO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ui-sounds");
        thread.setDaemon(true);
        return thread;
    });
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    private static volatile boolean enabled;
    private static volatile Clip hoverClip;
    private static volatile Clip clickClip;
    private static boolean listening;
    private static Component lastTarget;
    private static long lastHoverAt;

    private UiSounds() {
    }

    public static void apply(boolean on, int volume) {
        enabled = on;
        if (!on) {
            return;
        }
        if (!listening) {
            listening = true;
            Toolkit.getDefaultToolkit().addAWTEventListener(UiSounds::onMouse, AWTEvent.MOUSE_EVENT_MASK);
        }
        AUDIO.execute(() -> {
            Clip hover = open(hover(volume));
            Clip click = open(click(volume));
            close(hoverClip);
            close(clickClip);
            hoverClip = hover;
            clickClip = click;
        });
    }

    public static void preview(int volume) {
        AUDIO.execute(() -> {
            Clip clip = open(click(volume));
            if (clip != null) {
                clip.start();
                clip.drain();
                clip.close();
            }
        });
    }

    static byte[] hover(int volume) {
        return tone(volume, 0.55, 20, 2.5, 2_400, 3_700);
    }

    static byte[] click(int volume) {
        return tone(volume, 1.0, 35, 5, 1_300, 2_100);
    }

    static byte[] tone(int volume, double loudness, double millis, double decayMillis, double low, double high) {
        double level = Math.clamp(volume, 0, 100) / 100.0;
        double amplitude = 0.5 * loudness * level * level;
        int frames = (int) (SAMPLE_RATE * millis / 1000);
        byte[] data = new byte[frames * 2];
        for (int i = 0; i < frames; i++) {
            double seconds = i / SAMPLE_RATE;
            double attack = Math.min(1, seconds * 1000 / ATTACK_MILLIS);
            double envelope = attack * Math.exp(-seconds * 1000 / decayMillis);
            double wave = (Math.sin(2 * Math.PI * low * seconds) + 0.4 * Math.sin(2 * Math.PI * high * seconds)) / 1.4;
            short sample = (short) Math.round(wave * envelope * amplitude * Short.MAX_VALUE);
            data[2 * i] = (byte) sample;
            data[2 * i + 1] = (byte) (sample >> 8);
        }
        return data;
    }

    static Component target(Component component) {
        for (Component c = component; c != null; c = c.getParent()) {
            if (c instanceof JSpinner || c instanceof JScrollBar) {
                return null;
            }
            if (c instanceof JComboBox<?> || c instanceof JComponent marked
                    && Boolean.TRUE.equals(marked.getClientProperty(SOUND_TARGET))) {
                return c.isEnabled() ? c : null;
            }
        }
        return component instanceof AbstractButton button && button.isEnabled() ? button : null;
    }

    private static void onMouse(AWTEvent event) {
        if (!enabled || !(event instanceof MouseEvent mouse) || mouse.getComponent() == null) {
            return;
        }
        switch (mouse.getID()) {
            case MouseEvent.MOUSE_ENTERED -> entered(mouse);
            case MouseEvent.MOUSE_EXITED -> exited(mouse);
            case MouseEvent.MOUSE_PRESSED -> {
                if (SwingUtilities.isLeftMouseButton(mouse) && target(mouse.getComponent()) != null) {
                    play(clickClip);
                }
            }
            default -> {
            }
        }
    }

    private static void entered(MouseEvent mouse) {
        Component target = target(mouse.getComponent());
        if (target == null || target == lastTarget) {
            return;
        }
        lastTarget = target;
        long now = mouse.getWhen();
        if (now - lastHoverAt >= HOVER_GAP_MILLIS) {
            lastHoverAt = now;
            play(hoverClip);
        }
    }

    private static void exited(MouseEvent mouse) {
        if (lastTarget == null) {
            return;
        }
        if (!lastTarget.isShowing()) {
            lastTarget = null;
            return;
        }
        Point point = SwingUtilities.convertPoint(mouse.getComponent(), mouse.getPoint(), lastTarget);
        if (!lastTarget.contains(point)) {
            lastTarget = null;
        }
    }

    private static void play(Clip clip) {
        if (clip == null) {
            return;
        }
        AUDIO.execute(() -> {
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        });
    }

    private static Clip open(byte[] data) {
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(FORMAT, data, 0, data.length);
            return clip;
        } catch (Exception | LinkageError e) {
            LOG.log(Level.INFO, "Interface sounds are off: no sound output is available", e);
            enabled = false;
            return null;
        }
    }

    private static void close(Clip clip) {
        if (clip != null) {
            clip.close();
        }
    }
}
