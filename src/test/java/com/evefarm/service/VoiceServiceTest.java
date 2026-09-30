package com.evefarm.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

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
    void spokenTextCanNeverChangeTheVolume() {
        assertEquals("0 Aggro on Nozeu", VoiceService.clean("\u00010 Aggro on Nozeu"));
    }
}
