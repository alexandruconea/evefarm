package com.evefarm.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

public final class JdbcUtil {

    public static void setNullable(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    public static void setNullable(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value != null) {
            ps.setLong(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    public static void setNullable(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value != null) {
            ps.setDouble(index, value);
        } else {
            ps.setNull(index, Types.REAL);
        }
    }

    public static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    public static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private JdbcUtil() {
    }
}
