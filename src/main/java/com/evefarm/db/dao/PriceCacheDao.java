package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.JdbcUtil;
import com.evefarm.model.PriceBreakdown;
import com.evefarm.model.PriceMode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

public final class PriceCacheDao {

    private final Database database;

    public PriceCacheDao(Database database) {
        this.database = database;
    }

    public OptionalDouble findUnitPrice(int typeId, PriceMode mode) {
        String sql = """
                SELECT average_price, adjusted_price, sell_max, sell_avg, sell_median,
                       sell_percentile, sell_min, buy_max, buy_avg, buy_median, buy_percentile, buy_min
                FROM price_cache WHERE type_id = ?
                """;
        synchronized (database) {
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, typeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return OptionalDouble.empty();
                    }
                    Double value = resolveUnitPrice(rs, mode);
                    return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read price_cache for " + typeId, e);
            }
        }
    }

    public Map<Integer, Double> findAllUnitPrices(PriceMode mode) {
        String sql = """
                SELECT type_id, average_price, adjusted_price, sell_max, sell_avg, sell_median,
                       sell_percentile, sell_min, buy_max, buy_avg, buy_median, buy_percentile, buy_min
                FROM price_cache
                """;
        synchronized (database) {
            Map<Integer, Double> result = new HashMap<>();
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Double value = resolveUnitPrice(rs, mode);
                    if (value != null) {
                        result.put(rs.getInt("type_id"), value);
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read price_cache", e);
            }
            return result;
        }
    }

    private Double resolveUnitPrice(ResultSet rs, PriceMode mode) throws SQLException {
        Double specific = switch (mode) {
            case SELL_MAX -> JdbcUtil.getNullableDouble(rs, "sell_max");
            case SELL_AVG -> JdbcUtil.getNullableDouble(rs, "sell_avg");
            case SELL_MEDIAN -> JdbcUtil.getNullableDouble(rs, "sell_median");
            case SELL_PERCENTILE -> JdbcUtil.getNullableDouble(rs, "sell_percentile");
            case SELL_MIN -> JdbcUtil.getNullableDouble(rs, "sell_min");
            case BUY_MAX -> JdbcUtil.getNullableDouble(rs, "buy_max");
            case BUY_AVG -> JdbcUtil.getNullableDouble(rs, "buy_avg");
            case BUY_MEDIAN -> JdbcUtil.getNullableDouble(rs, "buy_median");
            case BUY_PERCENTILE -> JdbcUtil.getNullableDouble(rs, "buy_percentile");
            case BUY_MIN -> JdbcUtil.getNullableDouble(rs, "buy_min");
            case MIDPOINT -> midpoint(JdbcUtil.getNullableDouble(rs, "sell_min"),
                    JdbcUtil.getNullableDouble(rs, "buy_max"));
        };
        if (specific != null && specific > 0) {
            return specific;
        }
        double average = rs.getDouble("average_price");
        if (!rs.wasNull() && average > 0) {
            return average;
        }
        double adjusted = rs.getDouble("adjusted_price");
        return rs.wasNull() ? null : adjusted;
    }

    private Double midpoint(Double sellMin, Double buyMax) {
        boolean hasSell = sellMin != null && sellMin > 0;
        boolean hasBuy = buyMax != null && buyMax > 0;
        if (hasSell && hasBuy) {
            return (sellMin + buyMax) / 2;
        }
        if (hasSell) {
            return sellMin;
        }
        if (hasBuy) {
            return buyMax;
        }
        return null;
    }

    public record AverageAdjusted(Double average, Double adjusted) {
    }

    public void replaceAll(Map<Integer, AverageAdjusted> typeIdToAverageAdjusted) {
        synchronized (database) {
            Connection connection = database.connection();
            String sql = """
                    INSERT INTO price_cache(type_id, average_price, adjusted_price, updated_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(type_id) DO UPDATE SET
                      average_price = excluded.average_price,
                      adjusted_price = excluded.adjusted_price,
                      updated_at = excluded.updated_at
                    """;
            String now = Instant.now().toString();
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    for (Map.Entry<Integer, AverageAdjusted> entry : typeIdToAverageAdjusted.entrySet()) {
                        ps.setInt(1, entry.getKey());
                        JdbcUtil.setNullable(ps, 2, entry.getValue().average());
                        JdbcUtil.setNullable(ps, 3, entry.getValue().adjusted());
                        ps.setString(4, now);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw new IllegalStateException("Failed to replace price_cache", e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public void replaceAllDetailed(Map<Integer, PriceBreakdown> breakdown) {
        synchronized (database) {
        Connection connection = database.connection();
        String sql = """
                INSERT INTO price_cache(
                  type_id, average_price, adjusted_price,
                  sell_max, sell_avg, sell_median, sell_percentile, sell_min,
                  buy_max, buy_avg, buy_median, buy_percentile, buy_min,
                  sell_volume, buy_volume, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(type_id) DO UPDATE SET
                  average_price = excluded.average_price,
                  adjusted_price = excluded.adjusted_price,
                  sell_max = excluded.sell_max,
                  sell_avg = excluded.sell_avg,
                  sell_median = excluded.sell_median,
                  sell_percentile = excluded.sell_percentile,
                  sell_min = excluded.sell_min,
                  buy_max = excluded.buy_max,
                  buy_avg = excluded.buy_avg,
                  buy_median = excluded.buy_median,
                  buy_percentile = excluded.buy_percentile,
                  buy_min = excluded.buy_min,
                  sell_volume = excluded.sell_volume,
                  buy_volume = excluded.buy_volume,
                  updated_at = excluded.updated_at
                """;
        String now = Instant.now().toString();
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (Map.Entry<Integer, PriceBreakdown> entry : breakdown.entrySet()) {
                    PriceBreakdown b = entry.getValue();
                    double bestSell = firstNonNull(b.sellAvg(), b.sellMin(), b.sellMax(), b.sellMedian(), b.sellPercentile());
                    double bestBuy = firstNonNull(b.buyAvg(), b.buyMax(), b.buyMin(), b.buyMedian(), b.buyPercentile());
                    ps.setInt(1, entry.getKey());
                    ps.setDouble(2, bestSell);
                    ps.setDouble(3, bestBuy);
                    JdbcUtil.setNullable(ps, 4, b.sellMax());
                    JdbcUtil.setNullable(ps, 5, b.sellAvg());
                    JdbcUtil.setNullable(ps, 6, b.sellMedian());
                    JdbcUtil.setNullable(ps, 7, b.sellPercentile());
                    JdbcUtil.setNullable(ps, 8, b.sellMin());
                    JdbcUtil.setNullable(ps, 9, b.buyMax());
                    JdbcUtil.setNullable(ps, 10, b.buyAvg());
                    JdbcUtil.setNullable(ps, 11, b.buyMedian());
                    JdbcUtil.setNullable(ps, 12, b.buyPercentile());
                    JdbcUtil.setNullable(ps, 13, b.buyMin());
                    JdbcUtil.setNullable(ps, 14, b.sellVolume());
                    JdbcUtil.setNullable(ps, 15, b.buyVolume());
                    ps.setString(16, now);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
            throw new IllegalStateException("Failed to replace price_cache (detailed)", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
        }
    }

    private double firstNonNull(Double... values) {
        for (Double value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return 0;
    }

    public Map<Integer, Double> findAllSellVolumes() {
        synchronized (database) {
            Map<Integer, Double> result = new HashMap<>();
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT type_id, sell_volume FROM price_cache WHERE sell_volume IS NOT NULL");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt("type_id"), rs.getDouble("sell_volume"));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read price_cache sell volumes", e);
            }
            return result;
        }
    }

    public Map<Integer, Double> findAllBuyVolumes() {
        synchronized (database) {
            Map<Integer, Double> result = new HashMap<>();
            Connection connection = database.connection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT type_id, buy_volume FROM price_cache WHERE buy_volume IS NOT NULL");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt("type_id"), rs.getDouble("buy_volume"));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read price_cache buy volumes", e);
            }
            return result;
        }
    }

    public Set<Integer> findMissingTypeIds(Collection<Integer> typeIds) {
        List<Integer> distinct = typeIds.stream().distinct().toList();
        Set<Integer> known = new HashSet<>();
        int chunkSize = 500;
        synchronized (database) {
            Connection connection = database.connection();
            for (int start = 0; start < distinct.size(); start += chunkSize) {
                List<Integer> chunk = distinct.subList(start, Math.min(start + chunkSize, distinct.size()));
                String placeholders = chunk.stream().map(id -> "?").collect(Collectors.joining(","));
                String sql = "SELECT type_id FROM price_cache WHERE type_id IN (" + placeholders + ")";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    int index = 1;
                    for (Integer id : chunk) {
                        ps.setInt(index++, id);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            known.add(rs.getInt("type_id"));
                        }
                    }
                } catch (SQLException e) {
                    throw new IllegalStateException("Failed to read price_cache", e);
                }
            }
        }
        Set<Integer> missing = new HashSet<>(distinct);
        missing.removeAll(known);
        return missing;
    }
}
