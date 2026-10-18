package com.evefarm.update;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

final class TarFixture {

    record Entry(String name, char type, byte[] data, int mode, String link, boolean paxName) {
    }

    private TarFixture() {
    }

    static Entry file(String name, String content, int mode) {
        return new Entry(name, '0', content.getBytes(StandardCharsets.UTF_8), mode, "", name.length() > 100);
    }

    static Entry directory(String name) {
        return new Entry(name, '5', new byte[0], 0755, "", false);
    }

    static Entry link(String name, String target) {
        return new Entry(name, '2', new byte[0], 0777, target, false);
    }

    static Entry hardLink(String name, String target) {
        return new Entry(name, '1', new byte[0], 0644, target, false);
    }

    static Entry gnuLongName(String name, String content) {
        return new Entry(name, 'L', content.getBytes(StandardCharsets.UTF_8), 0644, "", false);
    }

    static byte[] tar(Entry... entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Entry entry : entries) {
            if (entry.type() == 'L') {
                byte[] longName = (entry.name() + "\0").getBytes(StandardCharsets.UTF_8);
                write(out, header("././@LongLink", 'L', longName.length, 0644, ""), longName);
                write(out, header(entry.name().substring(0, 100), '0', entry.data().length, entry.mode(), ""),
                        entry.data());
                continue;
            }
            if (entry.paxName()) {
                byte[] pax = paxRecord("path", entry.name());
                write(out, header("PaxHeaders/long", 'x', pax.length, 0644, ""), pax);
            }
            String headerName = entry.paxName() ? entry.name().substring(0, 100) : entry.name();
            write(out, header(headerName, entry.type(), entry.data().length, entry.mode(), entry.link()), entry.data());
        }
        out.write(new byte[1024]);
        return out.toByteArray();
    }

    static byte[] tarGz(Entry... entries) throws IOException {
        return gzip(tar(entries));
    }

    static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }
        return out.toByteArray();
    }

    static byte[] paxRecord(String key, String value) {
        String body = " " + key + "=" + value + "\n";
        int length = body.getBytes(StandardCharsets.UTF_8).length;
        int total = length + String.valueOf(length).length();
        if (String.valueOf(total).length() != String.valueOf(length).length()) {
            total++;
        }
        return (total + body).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] header(String name, char type, long size, int mode, String link) {
        byte[] header = new byte[512];
        put(header, 0, 100, name);
        put(header, 100, 8, String.format("%07o", mode));
        put(header, 108, 8, "0001750");
        put(header, 116, 8, "0001750");
        put(header, 124, 12, String.format("%011o", size));
        put(header, 136, 12, String.format("%011o", 1_760_000_000L));
        for (int i = 148; i < 156; i++) {
            header[i] = ' ';
        }
        header[156] = (byte) type;
        put(header, 157, 100, link);
        put(header, 257, 6, "ustar");
        put(header, 263, 2, "00");
        put(header, 265, 32, "runner");
        put(header, 297, 32, "runner");
        long checksum = 0;
        for (byte b : header) {
            checksum += b & 0xFF;
        }
        put(header, 148, 7, String.format("%06o", checksum));
        return header;
    }

    private static void write(ByteArrayOutputStream out, byte[] header, byte[] data) {
        out.writeBytes(header);
        out.writeBytes(data);
        int padding = (512 - data.length % 512) % 512;
        out.writeBytes(new byte[padding]);
    }

    private static void put(byte[] header, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, header, offset, Math.min(bytes.length, length));
    }
}
