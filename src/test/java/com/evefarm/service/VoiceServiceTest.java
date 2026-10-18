package com.evefarm.service;

import com.evefarm.util.AppPaths;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceServiceTest {

    @Test
    void theTextIsOneShortLine() {
        assertEquals("Aggro on Nozeu Barset", VoiceService.clean("Aggro on Nozeu\r\nBarset"));
        assertEquals(400, VoiceService.clean("x".repeat(900)).length());
        assertEquals("", VoiceService.clean(null));
    }

    @Test
    void theSpokenTextIsReadAsDataNotRunAsACommand() {
        List<String> command = VoiceService.command();

        assertEquals(AppPaths.powerShell(), command.getFirst());
        assertEquals("-EncodedCommand", command.get(command.size() - 2));
        String script = new String(Base64.getDecoder().decode(command.getLast()), StandardCharsets.UTF_16LE);
        assertTrue(script.contains("[Console]::In.ReadLine()"));
        assertTrue(script.contains("SpeakAsync($line)"));
        assertTrue(script.contains("$voice.Volume = [int]$line.Substring(1)"));
    }

    @Test
    void theVolumeStaysBetweenSilentAndFull() {
        VoiceService voice = new VoiceService();
        assertEquals(100, voice.volume());

        voice.setVolume(150);
        assertEquals(100, voice.volume());
        voice.setVolume(-5);
        assertEquals(0, voice.volume());
        voice.setVolume(35);
        assertEquals(35, voice.volume());
        assertEquals("\u000135\n", VoiceService.volumeCommand(35));
    }

    @Test
    void onLinuxTheFirstSpeechProgramFoundIsUsed() {
        Map<String, Path> installed = Map.of("espeak-ng", Path.of("/usr/bin/espeak-ng"),
                "espeak", Path.of("/usr/bin/espeak"));

        assertEquals(Optional.of(Path.of("/usr/bin/espeak-ng")),
                VoiceService.findSpeaker(name -> Optional.ofNullable(installed.get(name))));
        assertEquals(Optional.empty(), VoiceService.findSpeaker(name -> Optional.empty()));
    }

    @Test
    void theSpokenTextComesAfterTheOptionsSoItIsNeverReadAsOne() {
        assertEquals(List.of(Path.of("/usr/bin/spd-say").toString(), "-w", "-i", "40", "--", "-i 100 Aggro"),
                VoiceService.speakerCommand(Path.of("/usr/bin/spd-say"), 70, "-i 100 Aggro"));
        assertEquals(List.of(Path.of("/usr/bin/espeak-ng").toString(), "-a", "70", "--", "Aggro on Nozeu"),
                VoiceService.speakerCommand(Path.of("/usr/bin/espeak-ng"), 70, "Aggro on Nozeu"));
    }

    @Test
    void spokenTextCanNeverChangeTheVolume() {
        assertEquals("0 Aggro on Nozeu", VoiceService.clean("\u00010 Aggro on Nozeu"));
    }
}
