package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class AbyssVoicePanel extends JPanel {

    private static final DateTimeFormatter AGGRO_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final AppContext appContext;
    private final JCheckBox aggroCheck = new JCheckBox("Say who gets aggro");
    private final JLabel aggroLabel = new JLabel(" ");
    private final JSlider volumeSlider = new JSlider(0, 100, 100);
    private final JLabel volumeLabel = new JLabel(" ");
    private final Timer volumeSample = new Timer(400, e -> volumeChosen());

    AbyssVoicePanel(AppContext appContext) {
        super(new GridBagLayout());
        this.appContext = appContext;
        buildRows();

        aggroCheck.setToolTipText("Reads your characters' Gamelogs and says a character's name as soon as NPCs "
                + "start shooting at it");
        aggroCheck.setSelected("true".equals(
                appContext.settingsDao.getOrDefault(SettingsDao.ABYSS_AGGRO_VOICE, "false")));
        aggroCheck.addActionListener(e -> aggroToggled());
        appContext.aggroWatchService.addListener(alert -> SwingUtilities.invokeLater(() -> showAggro(alert)));

        int volume = Math.clamp(appContext.settingsDao.getInt(SettingsDao.VOICE_VOLUME, 100), 0, 100);
        volumeSlider.setValue(volume);
        volumeLabel.setText(volume + "%");
        appContext.voiceService.setVolume(volume);
        volumeSlider.setToolTipText("How loud the aggro announcements are");
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

    private void buildRows() {
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 0, 3, 16);
        aggroLabel.setPreferredSize(new Dimension(340, aggroLabel.getPreferredSize().height));
        AbyssPanel.addStatusRow(this, c, 0, "Aggro voice", aggroCheck, aggroLabel);
        volumeSlider.setPreferredSize(new Dimension(180, volumeSlider.getPreferredSize().height));
        AbyssPanel.addStatusRow(this, c, 1, "Volume", volumeSlider, volumeLabel);
    }

    private void aggroToggled() {
        appContext.settingsDao.set(SettingsDao.ABYSS_AGGRO_VOICE, String.valueOf(aggroCheck.isSelected()));
        updateLogWatch();
    }

    private void updateLogWatch() {
        boolean wanted = aggroCheck.isSelected();
        boolean folderFound = appContext.killService.hasGameLogDirectory();
        if (wanted && folderFound) {
            appContext.aggroWatchService.start();
        } else {
            appContext.aggroWatchService.stop();
        }
        aggroLabel.setText(!aggroCheck.isSelected() ? "Off"
                : folderFound ? "Listening for NPCs shooting at your characters"
                : "EVE's Gamelogs folder wasn't found - set it in Options > Settings");
    }

    private void showAggro(AggroWatchService.Alert alert) {
        if (aggroCheck.isSelected()) {
            appContext.voiceService.say("Aggro on " + alert.characterName());
            aggroLabel.setText("Last: " + alert.characterName() + ", from " + alert.attacker() + " at "
                    + AGGRO_TIME.format(alert.at()));
        }
    }

    private void volumeChosen() {
        int volume = volumeSlider.getValue();
        appContext.settingsDao.set(SettingsDao.VOICE_VOLUME, String.valueOf(volume));
        appContext.voiceService.say("Volume " + volume + " percent");
    }
}
