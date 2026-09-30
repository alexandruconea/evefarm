package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.service.AbyssSpawnCatalog;
import com.evefarm.service.AbyssTrackerService;
import com.evefarm.service.AggroWatchService;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

final class AbyssVoicePanel extends JPanel {

    private static final DateTimeFormatter AGGRO_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final Duration PENDING_ROOM_AGE = Duration.ofMinutes(1);

    private final AppContext appContext;
    private final Supplier<AbyssTrackerService.Phase> trackerPhase;
    private final JCheckBox aggroCheck = new JCheckBox("Say who gets aggro");
    private final JLabel aggroLabel = new JLabel(" ");
    private final JCheckBox roomCheck = new JCheckBox("Say the spawn and tips in each room");
    private final JLabel roomLabel = new JLabel(" ");
    private final JSlider volumeSlider = new JSlider(0, 100, 100);
    private final JLabel volumeLabel = new JLabel(" ");
    private final Timer volumeSample = new Timer(400, e -> volumeChosen());
    private AbyssSpawnCatalog.RoomReport pendingRoom;
    private Instant pendingRoomAt;

    AbyssVoicePanel(AppContext appContext, Supplier<AbyssTrackerService.Phase> trackerPhase) {
        super(new GridBagLayout());
        this.appContext = appContext;
        this.trackerPhase = trackerPhase;
        buildRows();

        aggroCheck.setToolTipText("Reads your characters' Gamelogs and says a character's name as soon as NPCs "
                + "start shooting at it");
        aggroCheck.setSelected("true".equals(
                appContext.settingsDao.getOrDefault(SettingsDao.ABYSS_AGGRO_VOICE, "false")));
        aggroCheck.addActionListener(e -> aggroToggled());
        appContext.aggroWatchService.addListener(alert -> SwingUtilities.invokeLater(() -> showAggro(alert)));
        roomCheck.setToolTipText("Reads your characters' Gamelogs and, a few seconds into each Abyss room, says "
                + "which NPCs are there, what e-war to expect and how to fight them");
        roomCheck.setSelected("true".equals(
                appContext.settingsDao.getOrDefault(SettingsDao.ABYSS_ROOM_VOICE, "false")));
        roomCheck.addActionListener(e -> roomToggled());
        appContext.aggroWatchService.addRoomListener(report -> SwingUtilities.invokeLater(() -> roomReported(report)));

        int volume = savedVolume();
        volumeSlider.setValue(volume);
        volumeLabel.setText(volume + "%");
        appContext.voiceService.setVolume(volume);
        volumeSlider.setToolTipText("How loud the aggro and room announcements are");
        volumeSample.setRepeats(false);
        volumeSlider.addChangeListener(e -> {
            volumeLabel.setText(volumeSlider.getValue() + "%");
            appContext.voiceService.setVolume(volumeSlider.getValue());
            if (!volumeSlider.getValueIsAdjusting()) {
                volumeSample.restart();
            }
        });
        updateLogWatch();
    }

    void trackingStarted() {
        appContext.aggroWatchService.resetRooms();
        pendingRoom = null;
    }

    void enteredAbyss() {
        if (pendingRoom != null && Duration.between(pendingRoomAt, Instant.now()).compareTo(PENDING_ROOM_AGE) < 0) {
            announceRoom(pendingRoom);
        }
        pendingRoom = null;
    }

    void leftAbyss() {
        appContext.aggroWatchService.resetRooms();
    }

    private void buildRows() {
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 0, 3, 16);
        for (JLabel label : List.of(aggroLabel, roomLabel)) {
            label.setPreferredSize(new Dimension(340, label.getPreferredSize().height));
        }
        AbyssPanel.addStatusRow(this, c, 0, "Aggro voice", aggroCheck, aggroLabel);
        AbyssPanel.addStatusRow(this, c, 1, "Rooms", roomCheck, roomLabel);
        volumeSlider.setPreferredSize(new Dimension(180, volumeSlider.getPreferredSize().height));
        AbyssPanel.addStatusRow(this, c, 2, "Volume", volumeSlider, volumeLabel);
    }

    private void aggroToggled() {
        appContext.settingsDao.set(SettingsDao.ABYSS_AGGRO_VOICE, String.valueOf(aggroCheck.isSelected()));
        updateLogWatch();
    }

    private void roomToggled() {
        appContext.settingsDao.set(SettingsDao.ABYSS_ROOM_VOICE, String.valueOf(roomCheck.isSelected()));
        pendingRoom = null;
        updateLogWatch();
    }

    private void updateLogWatch() {
        boolean wanted = aggroCheck.isSelected() || roomCheck.isSelected();
        boolean folderFound = appContext.killService.hasGameLogDirectory();
        if (wanted && folderFound) {
            appContext.aggroWatchService.start();
        } else {
            appContext.aggroWatchService.stop();
        }
        String missingFolder = "EVE's Gamelogs folder wasn't found - set it in Options > Settings";
        aggroLabel.setText(!aggroCheck.isSelected() ? "Off"
                : folderFound ? "Listening for NPCs shooting at your characters" : missingFolder);
        roomLabel.setText(!roomCheck.isSelected() ? "Off"
                : folderFound ? "Waiting for the first room" : missingFolder);
        roomLabel.setToolTipText(null);
    }

    private void showAggro(AggroWatchService.Alert alert) {
        if (aggroCheck.isSelected()) {
            appContext.voiceService.say("Aggro on " + alert.characterName());
            aggroLabel.setText("Last: " + alert.characterName() + ", from " + alert.attacker() + " at "
                    + AGGRO_TIME.format(alert.at()));
        }
    }

    private int savedVolume() {
        try {
            int volume = Integer.parseInt(appContext.settingsDao.getOrDefault(SettingsDao.VOICE_VOLUME, "100"));
            return Math.max(0, Math.min(100, volume));
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    private void volumeChosen() {
        int volume = volumeSlider.getValue();
        appContext.settingsDao.set(SettingsDao.VOICE_VOLUME, String.valueOf(volume));
        appContext.voiceService.say("Volume " + volume + " percent");
    }

    private void roomReported(AbyssSpawnCatalog.RoomReport report) {
        if (!roomCheck.isSelected()) {
            return;
        }
        if (trackerPhase.get() == AbyssTrackerService.Phase.WAITING) {
            pendingRoom = report;
            pendingRoomAt = Instant.now();
            return;
        }
        announceRoom(report);
    }

    private void announceRoom(AbyssSpawnCatalog.RoomReport report) {
        pendingRoom = null;
        appContext.voiceService.say(report.speech());
        roomLabel.setText(report.speech());
        roomLabel.setToolTipText(report.speech() + "  NPCs seen: " + String.join(", ", report.npcs()));
    }
}
