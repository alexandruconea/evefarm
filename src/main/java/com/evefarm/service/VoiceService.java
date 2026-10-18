package com.evefarm.service;

import com.evefarm.util.AppPaths;
import com.evefarm.util.Platform;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VoiceService {

    private static final Logger LOG = Logger.getLogger(VoiceService.class.getName());
    private static final int MAX_TEXT_LENGTH = 400;
    private static final long SPEAK_TIMEOUT_SECONDS = 30;
    private static final char VOLUME_COMMAND = '\u0001';
    private static final List<String> LINUX_SPEAKERS = List.of("spd-say", "espeak-ng", "espeak");
    private static final String SCRIPT = String.join("\n",
            "[Console]::InputEncoding = [System.Text.Encoding]::UTF8",
            "Add-Type -AssemblyName System.Speech",
            "$voice = New-Object System.Speech.Synthesis.SpeechSynthesizer",
            "$voice.Rate = 1",
            "while ($true) {",
            "  $line = [Console]::In.ReadLine()",
            "  if ($line -eq $null) { break }",
            "  if ($line.StartsWith([string][char]1)) { $voice.Volume = [int]$line.Substring(1); continue }",
            "  if ($line.Length -gt 0) { [void]$voice.SpeakAsync($line) }",
            "}");

    private final boolean windows = Platform.isWindows();
    private Process process;
    private Writer writer;
    private int volume = 100;
    private Optional<Path> speaker;
    private ExecutorService speaking;

    public synchronized void say(String text) {
        String clean = clean(text);
        if (clean.isEmpty()) {
            return;
        }
        if (!windows) {
            speakWithProgram(clean);
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

    public synchronized boolean isAvailable() {
        return windows || speaker().isPresent();
    }

    public synchronized void setVolume(int percent) {
        volume = Math.max(0, Math.min(100, percent));
        if (process == null || !process.isAlive() || writer == null) {
            return;
        }
        try {
            writer.write(volumeCommand(volume));
            writer.flush();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Couldn't change the voice volume", e);
            close();
        }
    }

    synchronized int volume() {
        return volume;
    }

    static String volumeCommand(int percent) {
        return VOLUME_COMMAND + String.valueOf(percent) + "\n";
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
        if (speaking != null) {
            speaking.shutdownNow();
            speaking = null;
        }
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
        return List.of(AppPaths.powerShell(), "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-EncodedCommand", encoded);
    }

    static Optional<Path> findSpeaker(Function<String, Optional<Path>> finder) {
        for (String name : LINUX_SPEAKERS) {
            Optional<Path> program = finder.apply(name);
            if (program.isPresent()) {
                return program;
            }
        }
        return Optional.empty();
    }

    static List<String> speakerCommand(Path program, int volume, String text) {
        String name = program.getFileName().toString();
        if (name.equals("spd-say")) {
            return List.of(program.toString(), "-w", "-i", String.valueOf(volume * 2 - 100), "--", text);
        }
        return List.of(program.toString(), "-a", String.valueOf(volume), "--", text);
    }

    private Optional<Path> speaker() {
        if (speaker == null) {
            speaker = findSpeaker(AppPaths::findExecutable);
            if (speaker.isEmpty()) {
                LOG.warning("No speech program found (spd-say, espeak-ng or espeak), so voice alerts stay silent");
            }
        }
        return speaker;
    }

    private void speakWithProgram(String text) {
        Optional<Path> program = speaker();
        if (program.isEmpty() || volume == 0) {
            return;
        }
        List<String> command = speakerCommand(program.get(), volume, text);
        if (speaking == null) {
            speaking = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "voice");
                thread.setDaemon(true);
                return thread;
            });
        }
        try {
            speaking.execute(() -> speak(command));
        } catch (RejectedExecutionException e) {
            LOG.log(Level.FINE, "The voice was closed before the alert could be spoken", e);
        }
    }

    private static void speak(List<String> command) {
        try {
            Process speech = new ProcessBuilder(command)
                    .directory(new File(System.getProperty("user.home")))
                    .redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!speech.waitFor(SPEAK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                speech.destroy();
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Couldn't speak the alert", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
        writer.write(volumeCommand(volume));
    }
}
