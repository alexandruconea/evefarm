package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinuxNotificationsTest {

    @Test
    void theTextComesAfterTheOptionsAndItsMarkupIsEscaped() {
        Path notifySend = Path.of("/usr/bin/notify-send");
        Path icon = Path.of("/home/alex/EVEFarm/lib/EVEFarm.png");

        ProcessBuilder command = LinuxNotifications.command(notifySend, "--urgency=critical",
                "<b>Tritanium</b> & co", Optional.of(icon));

        assertEquals(List.of(notifySend.toString(), "--app-name=EVE Farm", "--icon=" + icon, "--",
                "--urgency=critical", "&lt;b&gt;Tritanium&lt;/b&gt; &amp; co"), command.command());
    }

    @Test
    void withoutAnIconOnlyTheTextIsSent() {
        Path notifySend = Path.of("/usr/bin/notify-send");

        ProcessBuilder command = LinuxNotifications.command(notifySend, "Skill queue", "Empty", Optional.empty());

        assertEquals(List.of(notifySend.toString(), "--app-name=EVE Farm", "--", "Skill queue", "Empty"),
                command.command());
    }
}
