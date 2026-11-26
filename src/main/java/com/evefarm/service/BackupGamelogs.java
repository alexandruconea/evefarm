package com.evefarm.service;

import org.sqlite.SQLiteConfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

final class BackupGamelogs {

    private static final Logger LOG = Logger.getLogger(BackupGamelogs.class.getName());
    static final String TABLE = "backup_gamelog";
    private static final Pattern LOG_NAME = Pattern.compile("[^/\\\\:]+\\.txt");

    record Restored(int files, Path directory, boolean included) {
    }

    private BackupGamelogs() {
    }

    static int addTo(Path backup, Path gamelogDirectory) {
        if (gamelogDirectory == null || !Files.isDirectory(gamelogDirectory)) {
            return 0;
        }
        try (Connection connection = new SQLiteConfig().createConnection("jdbc:sqlite:" + backup)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " (file_name TEXT PRIMARY KEY, "
                        + "modified_at INTEGER NOT NULL, size INTEGER NOT NULL, content BLOB NOT NULL)");
            }
            int count = 0;
            try (DirectoryStream<Path> files = Files.newDirectoryStream(gamelogDirectory, "*.txt");
                 PreparedStatement insert = connection.prepareStatement("INSERT OR REPLACE INTO " + TABLE
                         + " (file_name, modified_at, size, content) VALUES (?, ?, ?, ?)")) {
                for (Path file : files) {
                    if (!Files.isRegularFile(file)) {
                        continue;
                    }
                    byte[] content = Files.readAllBytes(file);
                    insert.setString(1, file.getFileName().toString());
                    insert.setLong(2, Files.getLastModifiedTime(file).toMillis());
                    insert.setLong(3, content.length);
                    insert.setBytes(4, gzip(content));
                    insert.addBatch();
                    count++;
                }
                insert.executeBatch();
            }
            connection.commit();
            return count;
        } catch (SQLException | IOException e) {
            LOG.log(Level.WARNING, "The Gamelogs in " + gamelogDirectory + " couldn't be added to the backup", e);
            return 0;
        }
    }

    static Restored restoreFrom(Connection staged, Path gamelogDirectory) throws SQLException, IOException {
        if (!hasTable(staged)) {
            return new Restored(0, gamelogDirectory, false);
        }
        int written = 0;
        if (gamelogDirectory != null) {
            Path directory = gamelogDirectory.toAbsolutePath().normalize();
            try (Statement statement = staged.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT file_name, modified_at, size, content FROM " + TABLE)) {
                while (rs.next()) {
                    if (restore(directory, rs.getString("file_name"), rs.getLong("modified_at"),
                            rs.getLong("size"), rs.getBytes("content"))) {
                        written++;
                    }
                }
            }
        }
        try (Statement statement = staged.createStatement()) {
            statement.execute("DROP TABLE " + TABLE);
        }
        return new Restored(written, gamelogDirectory, true);
    }

    static boolean restore(Path directory, String name, long modifiedAt, long size, byte[] content)
            throws IOException {
        if (name == null || !LOG_NAME.matcher(name).matches()) {
            LOG.warning("Left out a Gamelog with an unexpected name from the restore: " + name);
            return false;
        }
        Path target = directory.resolve(name).normalize();
        if (!directory.equals(target.getParent())) {
            return false;
        }
        if (Files.exists(target) && Files.size(target) >= size) {
            return false;
        }
        Files.createDirectories(directory);
        Path partial = directory.resolve("." + name + "." + UUID.randomUUID() + ".partial");
        try {
            Files.write(partial, gunzip(content));
            Files.setLastModifiedTime(partial, FileTime.fromMillis(modifiedAt));
            BackupRestoreService.moveReplacing(partial, target);
        } finally {
            Files.deleteIfExists(partial);
        }
        return true;
    }

    private static boolean hasTable(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, TABLE);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static byte[] gzip(byte[] content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(content.length / 8 + 64);
        try (GZIPOutputStream zip = new GZIPOutputStream(out)) {
            zip.write(content);
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] content) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(content))) {
            return in.readAllBytes();
        }
    }
}
