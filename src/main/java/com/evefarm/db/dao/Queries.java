package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.Corporation;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class Queries {

    @FunctionalInterface
    interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    @FunctionalInterface
    interface RowReader<T> {
        T read(ResultSet rs) throws SQLException;
    }

    private Queries() {
    }

    static <T> List<T> list(Database database, String sql, String what, Binder binder, RowReader<T> reader) {
        synchronized (database) {
            List<T> rows = new ArrayList<>();
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                binder.bind(ps);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(reader.read(rs));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read " + what, e);
            }
            return rows;
        }
    }

    static double sum(Database database, String sql, long corporationId) {
        List<Double> sums = list(database, sql, "the totals of corporation " + corporationId,
                ps -> ps.setLong(1, corporationId), rs -> rs.getDouble(1));
        return sums.isEmpty() ? 0 : sums.getFirst();
    }

    static String label(String name, String ticker) {
        return new Corporation(0, name, ticker).label();
    }
}
