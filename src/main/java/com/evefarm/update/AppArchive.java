package com.evefarm.update;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class AppArchive {

    static final String APP_FOLDER = "EVEFarm";
    private static final long MAX_EXTRACTED_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 10_000;
    private static final int BLOCK = 512;
    private static final int MAX_HEADER_DATA = 64 * 1024;
    private static final int NAME = 0;
    private static final int MODE = 100;
    private static final int SIZE = 124;
    private static final int CHECKSUM = 148;
    private static final int TYPE = 156;
    private static final int LINK_NAME = 157;
    private static final int MAGIC = 257;
    private static final int PREFIX = 345;
    private static final byte[] USTAR_MAGIC = "ustar\0".getBytes(StandardCharsets.US_ASCII);

    private AppArchive() {
    }

    static void extract(Path archive, Path target) throws IOException {
        if (archive.getFileName().toString().endsWith(".tar.gz")) {
            extractTarGz(archive, target, MAX_EXTRACTED_BYTES, MAX_ENTRIES);
        } else {
            extractZip(archive, target, MAX_EXTRACTED_BYTES, MAX_ENTRIES);
        }
    }

    static void extractZip(Path zip, Path target, long maxExtractedBytes, int maxEntries) throws IOException {
        checkLimits(maxExtractedBytes, maxEntries);
        Path root = createRoot(target);
        Budget budget = new Budget(maxExtractedBytes, maxEntries);
        byte[] buffer = new byte[64 * 1024];
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                budget.countEntry();
                Optional<Path> destination = destination(root, entry.getName());
                if (destination.isEmpty()) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination.get());
                } else {
                    Files.createDirectories(destination.get().getParent());
                    try (OutputStream out = Files.newOutputStream(destination.get())) {
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            budget.countBytes(read);
                            out.write(buffer, 0, read);
                        }
                    }
                }
                in.closeEntry();
            }
        }
    }

    static void extractTarGz(Path archive, Path target, long maxExtractedBytes, int maxEntries) throws IOException {
        checkLimits(maxExtractedBytes, maxEntries);
        Path root = createRoot(target);
        Budget budget = new Budget(maxExtractedBytes, maxEntries);
        boolean posix = root.getFileSystem().supportedFileAttributeViews().contains("posix");
        List<Link> links = new ArrayList<>();
        try (InputStream in = new BufferedInputStream(new GZIPInputStream(Files.newInputStream(archive), 64 * 1024))) {
            byte[] header = new byte[BLOCK];
            Map<String, String> extended = new HashMap<>();
            while (readBlock(in, header)) {
                budget.countEntry();
                checkChecksum(header);
                char type = (char) header[TYPE];
                long size = octal(header, SIZE, 12);
                if (type == 'x' || type == 'L' || type == 'K') {
                    readExtended(type, readData(in, size), extended);
                    continue;
                }
                if (type == 'g') {
                    readData(in, size);
                    continue;
                }
                String name = extended.getOrDefault("path", headerName(header));
                String link = extended.getOrDefault("linkpath", text(header, LINK_NAME, 100));
                if (extended.containsKey("size")) {
                    size = parseSize(extended.get("size"));
                }
                extended.clear();
                Optional<Path> destination = destination(root, name);
                switch (type) {
                    case '0', '\0', '7' -> writeFile(in, size, destination, budget,
                            posix ? octal(header, MODE, 8) : -1);
                    case '5' -> {
                        skipData(in, size);
                        if (destination.isPresent()) {
                            Files.createDirectories(destination.get());
                        }
                    }
                    case '2' -> {
                        skipData(in, size);
                        if (destination.isPresent()) {
                            links.add(new Link(destination.get(), link, name));
                        }
                    }
                    default -> throw new IOException("Unsupported entry in the update: " + name);
                }
            }
        }
        createLinks(root, links);
    }

    private record Link(Path path, String target, String entryName) {
    }

    private static void checkLimits(long maxExtractedBytes, int maxEntries) {
        if (maxExtractedBytes < 1 || maxEntries < 1) {
            throw new IllegalArgumentException("Extraction limits must be positive");
        }
    }

    private static Path createRoot(Path target) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root;
    }

    private static Optional<Path> destination(Path root, String entryName) throws IOException {
        String name = entryName.replace('\\', '/');
        if (!name.startsWith(APP_FOLDER + "/")) {
            throw new IOException("Unexpected file in the update: " + entryName);
        }
        String relative = name.substring(APP_FOLDER.length() + 1);
        if (relative.isEmpty() || relative.equals("/")) {
            return Optional.empty();
        }
        Path destination = root.resolve(relative).normalize();
        if (!destination.startsWith(root) || destination.equals(root)) {
            throw new IOException("Unsafe path in the update: " + entryName);
        }
        return Optional.of(destination);
    }

    private static void writeFile(InputStream in, long size, Optional<Path> destination, Budget budget, long mode)
            throws IOException {
        if (destination.isEmpty()) {
            throw new IOException("The update contains a file without a name.");
        }
        Path file = destination.get();
        Files.createDirectories(file.getParent());
        byte[] buffer = new byte[64 * 1024];
        long remaining = size;
        try (OutputStream out = Files.newOutputStream(file)) {
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new EOFException("The update archive ends in the middle of a file.");
                }
                budget.countBytes(read);
                out.write(buffer, 0, read);
                remaining -= read;
            }
        }
        skipFully(in, padding(size));
        if (mode >= 0) {
            Files.setPosixFilePermissions(file, permissions((int) mode | 0600));
        }
    }

    private static void createLinks(Path root, List<Link> links) throws IOException {
        Path realRoot = root.toRealPath();
        for (Link link : links) {
            Path parent = link.path().getParent();
            if (link.target().isEmpty() || link.target().startsWith("/")
                    || !parent.resolve(link.target()).normalize().startsWith(root)
                    || passesThroughLink(root, parent)) {
                throw new IOException("Unsafe link in the update: " + link.entryName());
            }
            Files.createDirectories(parent);
            Files.createSymbolicLink(link.path(), link.path().getFileSystem().getPath(link.target()));
        }
        for (Link link : links) {
            Path resolved;
            try {
                resolved = link.path().toRealPath();
            } catch (IOException e) {
                throw new IOException("A link in the update points to nothing: " + link.entryName(), e);
            }
            if (!resolved.startsWith(realRoot)) {
                throw new IOException("Unsafe link in the update: " + link.entryName());
            }
        }
    }

    private static boolean passesThroughLink(Path root, Path directory) {
        for (Path current = directory; current != null && !current.equals(root); current = current.getParent()) {
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }

    static Set<PosixFilePermission> permissions(int mode) {
        int allowed = mode & 0755;
        Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        PosixFilePermission[] order = {
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE};
        for (int bit = 0; bit < order.length; bit++) {
            if ((allowed & (0400 >> bit)) != 0) {
                permissions.add(order[bit]);
            }
        }
        return permissions;
    }

    private static boolean readBlock(InputStream in, byte[] header) throws IOException {
        int filled = in.readNBytes(header, 0, BLOCK);
        if (filled == 0) {
            throw new EOFException("The update archive ends without its end marker.");
        }
        if (filled < BLOCK) {
            throw new EOFException("The update archive ends unexpectedly.");
        }
        for (byte b : header) {
            if (b != 0) {
                return true;
            }
        }
        return false;
    }

    private static void checkChecksum(byte[] header) throws IOException {
        long expected = octal(header, CHECKSUM, 8);
        long unsigned = 0;
        long signed = 0;
        for (int i = 0; i < BLOCK; i++) {
            byte value = i >= CHECKSUM && i < CHECKSUM + 8 ? (byte) ' ' : header[i];
            unsigned += value & 0xFF;
            signed += value;
        }
        if (expected != unsigned && expected != signed) {
            throw new IOException("The update archive is damaged (bad header checksum).");
        }
    }

    private static byte[] readData(InputStream in, long size) throws IOException {
        if (size > MAX_HEADER_DATA) {
            throw new IOException("The update archive has an oversized header.");
        }
        byte[] data = in.readNBytes((int) size);
        if (data.length < size) {
            throw new EOFException("The update archive ends unexpectedly.");
        }
        skipFully(in, padding(size));
        return data;
    }

    private static void skipData(InputStream in, long size) throws IOException {
        skipFully(in, size + padding(size));
    }

    private static void readExtended(char type, byte[] data, Map<String, String> extended) throws IOException {
        if (type == 'L') {
            extended.put("path", nulTerminated(data));
        } else if (type == 'K') {
            extended.put("linkpath", nulTerminated(data));
        } else {
            extended.putAll(paxRecords(data));
        }
    }

    static Map<String, String> paxRecords(byte[] data) throws IOException {
        Map<String, String> records = new HashMap<>();
        int position = 0;
        while (position < data.length) {
            int space = position;
            while (space < data.length && data[space] != ' ') {
                space++;
            }
            int length;
            try {
                length = Integer.parseInt(new String(data, position, space - position, StandardCharsets.US_ASCII));
            } catch (NumberFormatException e) {
                throw new IOException("The update archive has a damaged extended header.", e);
            }
            int end = position + length;
            if (length <= 0 || end > data.length || space >= end || data[end - 1] != '\n') {
                throw new IOException("The update archive has a damaged extended header.");
            }
            String record = new String(data, space + 1, end - space - 2, StandardCharsets.UTF_8);
            int equals = record.indexOf('=');
            if (equals <= 0) {
                throw new IOException("The update archive has a damaged extended header.");
            }
            records.put(record.substring(0, equals), record.substring(equals + 1));
            position = end;
        }
        return records;
    }

    private static String headerName(byte[] header) {
        String name = text(header, NAME, 100);
        if (!Arrays.equals(header, MAGIC, MAGIC + USTAR_MAGIC.length, USTAR_MAGIC, 0, USTAR_MAGIC.length)) {
            return name;
        }
        String prefix = text(header, PREFIX, 155);
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    private static String text(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static String nulTerminated(byte[] data) {
        int end = 0;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        return new String(data, 0, end, StandardCharsets.UTF_8);
    }

    static long octal(byte[] header, int offset, int length) throws IOException {
        if ((header[offset] & 0x80) != 0) {
            throw new IOException("The update archive contains a file that is too large.");
        }
        long value = 0;
        boolean digits = false;
        for (int i = offset; i < offset + length; i++) {
            byte b = header[i];
            if (b == 0 || (b == ' ' && digits)) {
                break;
            }
            if (b == ' ') {
                continue;
            }
            if (b < '0' || b > '7') {
                throw new IOException("The update archive is damaged (bad number in a header).");
            }
            int digit = b - '0';
            value = value * 8 + digit;
            digits = true;
        }
        return value;
    }

    private static long parseSize(String value) throws IOException {
        try {
            long size = Long.parseLong(value.strip());
            if (size < 0) {
                throw new IOException("The update archive has a negative file size.");
            }
            return size;
        } catch (NumberFormatException e) {
            throw new IOException("The update archive has a damaged file size.", e);
        }
    }

    private static long padding(long size) {
        long remainder = size % BLOCK;
        return remainder == 0 ? 0 : BLOCK - remainder;
    }

    private static void skipFully(InputStream in, long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    throw new EOFException("The update archive ends unexpectedly.");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private static final class Budget {
        private final long maxBytes;
        private final int maxEntries;
        private long bytes;
        private int entries;

        Budget(long maxBytes, int maxEntries) {
            this.maxBytes = maxBytes;
            this.maxEntries = maxEntries;
        }

        void countEntry() throws IOException {
            entries++;
            if (entries > maxEntries) {
                throw new IOException("The update contains too many files - stopped.");
            }
        }

        void countBytes(long count) throws IOException {
            bytes += count;
            if (bytes > maxBytes) {
                throw new IOException("The unpacked update is too large - stopped.");
            }
        }
    }
}
