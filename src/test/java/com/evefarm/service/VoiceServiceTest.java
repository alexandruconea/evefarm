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
        assertEquals(200, VoiceService.clean("x".repeat(500)).length());
        assertEquals("", VoiceService.clean(null));
    }

    @Test
    void theSpokenTextIsReadAsDataNotRunAsACommand() {
        List<String> command = VoiceService.command();

        assertEquals("-EncodedCommand", command.get(command.size() - 2));
        String script = new String(Base64.getDecoder().decode(command.getLast()), StandardCharsets.UTF_16LE);
        assertTrue(script.contains("[Console]::In.ReadLine()"));
        assertTrue(script.contains("SpeakAsync($line)"));
    }
}
