package com.evefarm.service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VoiceService {

    private static final Logger LOG = Logger.getLogger(VoiceService.class.getName());
    private static final int MAX_TEXT_LENGTH = 200;
    private static final String SCRIPT = String.join("\n",
            "[Console]::InputEncoding = [System.Text.Encoding]::UTF8",
            "Add-Type -AssemblyName System.Speech",
            "$voice = New-Object System.Speech.Synthesis.SpeechSynthesizer",
            "$voice.Rate = 1",
            "while ($true) {",
            "  $line = [Console]::In.ReadLine()",
            "  if ($line -eq $null) { break }",
            "  if ($line.Length -gt 0) { [void]$voice.SpeakAsync($line) }",
            "}");

    private Process process;
    private Writer writer;

    public synchronized void say(String text) {
        String clean = clean(text);
        if (clean.isEmpty()) {
            return;
        }
        try {
            ensureStarted();
            writer.write(clean + "\n");
            writer.flush();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Couldn't speak the alert", e);
            close();
        }
    }

    public synchronized void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
        }
        if (process != null && process.isAlive()) {
            process.destroy();
        }
        process = null;
    }

    static String clean(String text) {
        if (text == null) {
            return "";
        }
        String single = text.replaceAll("[\\p{Cntrl}]+", " ").strip();
        return single.length() <= MAX_TEXT_LENGTH ? single : single.substring(0, MAX_TEXT_LENGTH);
    }

    static List<String> command() {
        String encoded = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-EncodedCommand", encoded);
    }

    private void ensureStarted() throws IOException {
        if (process != null && process.isAlive() && writer != null) {
            return;
        }
        process = new ProcessBuilder(command())
                .directory(new File(System.getProperty("user.home")))
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
    }
}
