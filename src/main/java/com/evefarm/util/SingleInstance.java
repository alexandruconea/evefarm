package com.evefarm.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public final class SingleInstance implements AutoCloseable {

    private static final String LOCK_FILE = "instance.lock";
    private static final String PORT_FILE = "instance.port";
    private static final String SHOW_COMMAND = "show";
    private static final int CONNECT_TIMEOUT_MILLIS = 2000;

    private final FileChannel channel;
    private final FileLock lock;
    private final ServerSocket server;
    private volatile Runnable onShowRequested = () -> {
    };

    private SingleInstance(FileChannel channel, FileLock lock, ServerSocket server) {
        this.channel = channel;
        this.lock = lock;
        this.server = server;
    }

    public static Optional<SingleInstance> acquire(Path directory) throws IOException {
        Files.createDirectories(directory);
        FileChannel channel = FileChannel.open(directory.resolve(LOCK_FILE),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            lock = null;
        } catch (IOException e) {
            channel.close();
            throw e;
        }
        if (lock == null) {
            channel.close();
            return Optional.empty();
        }
        ServerSocket server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        Files.writeString(directory.resolve(PORT_FILE), String.valueOf(server.getLocalPort()), StandardCharsets.US_ASCII);
        SingleInstance instance = new SingleInstance(channel, lock, server);
        Thread listener = new Thread(instance::listen, "single-instance");
        listener.setDaemon(true);
        listener.start();
        return Optional.of(instance);
    }

    public static boolean signalRunningInstance(Path directory) {
        try {
            int port = Integer.parseInt(Files.readString(directory.resolve(PORT_FILE), StandardCharsets.US_ASCII).trim());
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port),
                        CONNECT_TIMEOUT_MILLIS);
                OutputStream out = socket.getOutputStream();
                out.write((SHOW_COMMAND + "\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
            }
            return true;
        } catch (IOException | NumberFormatException e) {
            return false;
        }
    }

    public void onShowRequested(Runnable action) {
        this.onShowRequested = action;
    }

    private void listen() {
        while (!server.isClosed()) {
            try (Socket client = server.accept()) {
                client.setSoTimeout(CONNECT_TIMEOUT_MILLIS);
                if (SHOW_COMMAND.equals(readCommand(client.getInputStream()))) {
                    onShowRequested.run();
                }
            } catch (IOException e) {
                if (server.isClosed()) {
                    return;
                }
            }
        }
    }

    private static String readCommand(InputStream in) throws IOException {
        byte[] buffer = new byte[16];
        int length = 0;
        int next;
        while (length < buffer.length && (next = in.read()) != -1 && next != '\n') {
            buffer[length++] = (byte) next;
        }
        return new String(buffer, 0, length, StandardCharsets.US_ASCII).trim();
    }

    @Override
    public void close() {
        try {
            server.close();
        } catch (IOException ignored) {
        }
        try {
            lock.release();
            channel.close();
        } catch (IOException ignored) {
        }
    }
}
