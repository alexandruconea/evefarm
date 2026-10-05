package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.model.BlueprintChoice;
import com.evefarm.model.Decryptor;
import com.evefarm.model.IndustryActivity;
import com.evefarm.model.IndustryCatalog;
import com.evefarm.model.IndustryType;
import com.evefarm.model.SkillRequirement;
import com.evefarm.model.SolarSystem;
import com.evefarm.model.TypeQuantity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class IndustryCatalogDao {

    private static final List<String> TABLES = List.of("sde_industry_type", "sde_industry_activity",
            "sde_industry_material", "sde_industry_product", "sde_industry_skill", "sde_decryptor",
            "sde_solar_system");

    private final Database database;

    public IndustryCatalogDao(Database database) {
        this.database = database;
    }

    public void replaceAll(IndustryCatalog catalog, List<Decryptor> decryptors, List<SolarSystem> solarSystems) {
        synchronized (database) {
            Connection connection = database.connection();
            try {
                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    for (String table : TABLES) {
                        statement.executeUpdate("DELETE FROM " + table);
                    }
                }
                insertTypes(connection, catalog.types());
                insertActivities(connection, catalog.activities());
                insertDecryptors(connection, decryptors);
                insertSolarSystems(connection, solarSystems);
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
                throw new IllegalStateException("Failed to save the industry data", e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    private static void insertTypes(Connection connection, List<IndustryType> types) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO sde_industry_type(type_id, name, group_name, category_name, published)
                VALUES (?, ?, ?, ?, ?)
                """)) {
            for (IndustryType type : types) {
                ps.setInt(1, type.typeId());
                ps.setString(2, type.name());
                ps.setString(3, type.groupName());
                ps.setString(4, type.categoryName());
                ps.setInt(5, type.published() ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertActivities(Connection connection, List<IndustryActivity> activities)
            throws SQLException {
        try (PreparedStatement activity = connection.prepareStatement(
                "INSERT INTO sde_industry_activity(blueprint_id, activity_id, time) VALUES (?, ?, ?)");
             PreparedStatement material = connection.prepareStatement(
                     "INSERT OR REPLACE INTO sde_industry_material(blueprint_id, activity_id, material_id, quantity) "
                             + "VALUES (?, ?, ?, ?)");
             PreparedStatement product = connection.prepareStatement(
                     "INSERT OR REPLACE INTO sde_industry_product(blueprint_id, activity_id, product_id, quantity, "
                             + "probability) VALUES (?, ?, ?, ?, ?)");
             PreparedStatement skill = connection.prepareStatement(
                     "INSERT OR REPLACE INTO sde_industry_skill(blueprint_id, activity_id, skill_id, level) "
                             + "VALUES (?, ?, ?, ?)")) {
            for (IndustryActivity entry : activities) {
                activity.setInt(1, entry.blueprintId());
                activity.setInt(2, entry.activityId());
                activity.setLong(3, entry.timeSeconds());
                activity.addBatch();
                for (TypeQuantity input : entry.materials()) {
                    material.setInt(1, entry.blueprintId());
                    material.setInt(2, entry.activityId());
                    material.setInt(3, input.typeId());
                    material.setLong(4, input.quantity());
                    material.addBatch();
                }
                for (TypeQuantity output : entry.products()) {
                    product.setInt(1, entry.blueprintId());
                    product.setInt(2, entry.activityId());
                    product.setInt(3, output.typeId());
                    product.setLong(4, output.quantity());
                    Double probability = entry.probabilities().get(output.typeId());
                    if (probability == null) {
                        product.setNull(5, Types.REAL);
                    } else {
                        product.setDouble(5, probability);
                    }
                    product.addBatch();
                }
                for (SkillRequirement requirement : entry.skills()) {
                    skill.setInt(1, entry.blueprintId());
                    skill.setInt(2, entry.activityId());
                    skill.setInt(3, requirement.skillId());
                    skill.setInt(4, requirement.level());
                    skill.addBatch();
                }
            }
            activity.executeBatch();
            material.executeBatch();
            product.executeBatch();
            skill.executeBatch();
        }
    }

    private static void insertDecryptors(Connection connection, List<Decryptor> decryptors) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO sde_decryptor(type_id, name, probability_multiplier, me_modifier, te_modifier,
                                          runs_modifier)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            for (Decryptor decryptor : decryptors) {
                ps.setInt(1, decryptor.typeId());
                ps.setString(2, decryptor.name());
                ps.setDouble(3, decryptor.probabilityMultiplier());
                ps.setInt(4, decryptor.meModifier());
                ps.setInt(5, decryptor.teModifier());
                ps.setInt(6, decryptor.runsModifier());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertSolarSystems(Connection connection, List<SolarSystem> solarSystems)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO sde_solar_system(solar_system_id, name, security, region_name) VALUES (?, ?, ?, ?)")) {
            for (SolarSystem system : solarSystems) {
                ps.setLong(1, system.systemId());
                ps.setString(2, system.name());
                ps.setDouble(3, system.security());
                ps.setString(4, system.regionName());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public int countActivities() {
        return count("sde_industry_activity");
    }

    public int countSolarSystems() {
        return count("sde_solar_system");
    }

    private int count(String table) {
        synchronized (database) {
            try (Statement statement = database.connection().createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                return rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to count the industry data", e);
            }
        }
    }

    public List<SolarSystem> solarSystems() {
        synchronized (database) {
            try (Statement statement = database.connection().createStatement();
                 ResultSet rs = statement.executeQuery("""
                         SELECT solar_system_id, name, security, region_name
                         FROM sde_solar_system ORDER BY name COLLATE NOCASE
                         """)) {
                List<SolarSystem> systems = new ArrayList<>();
                while (rs.next()) {
                    systems.add(new SolarSystem(rs.getLong(1), rs.getString(2), rs.getDouble(3), rs.getString(4)));
                }
                return systems;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the solar systems", e);
            }
        }
    }

    public List<BlueprintChoice> manufacturingChoices() {
        String sql = """
                SELECT p.blueprint_id, p.product_id, t.name, t.group_name, t.category_name
                FROM sde_industry_product p
                JOIN sde_industry_type t ON t.type_id = p.product_id
                JOIN sde_industry_type b ON b.type_id = p.blueprint_id
                WHERE p.activity_id = ? AND t.published = 1 AND b.published = 1
                ORDER BY t.name COLLATE NOCASE
                """;
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setInt(1, IndustryActivity.MANUFACTURING);
                List<BlueprintChoice> choices = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        choices.add(new BlueprintChoice(rs.getInt("blueprint_id"), rs.getInt("product_id"),
                                rs.getString("name"), rs.getString("group_name"), rs.getString("category_name")));
                    }
                }
                return choices;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to list the blueprints", e);
            }
        }
    }

    public Optional<IndustryActivity> activity(int blueprintId, int activityId) {
        synchronized (database) {
            Connection connection = database.connection();
            try {
                Long time = null;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT time FROM sde_industry_activity WHERE blueprint_id = ? AND activity_id = ?")) {
                    ps.setInt(1, blueprintId);
                    ps.setInt(2, activityId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            time = rs.getLong(1);
                        }
                    }
                }
                if (time == null) {
                    return Optional.empty();
                }
                List<TypeQuantity> materials = quantities(connection,
                        "SELECT material_id, quantity FROM sde_industry_material WHERE blueprint_id = ? "
                                + "AND activity_id = ? ORDER BY material_id", blueprintId, activityId);
                List<TypeQuantity> products = new ArrayList<>();
                Map<Integer, Double> probabilities = new HashMap<>();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT product_id, quantity, probability FROM sde_industry_product WHERE blueprint_id = ? "
                                + "AND activity_id = ?")) {
                    ps.setInt(1, blueprintId);
                    ps.setInt(2, activityId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            products.add(new TypeQuantity(rs.getInt(1), rs.getLong(2)));
                            double value = rs.getDouble(3);
                            if (!rs.wasNull()) {
                                probabilities.put(rs.getInt(1), value);
                            }
                        }
                    }
                }
                List<SkillRequirement> skills = new ArrayList<>();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT skill_id, level FROM sde_industry_skill WHERE blueprint_id = ? AND activity_id = ?")) {
                    ps.setInt(1, blueprintId);
                    ps.setInt(2, activityId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            skills.add(new SkillRequirement(rs.getInt(1), rs.getInt(2)));
                        }
                    }
                }
                return Optional.of(new IndustryActivity(blueprintId, activityId, time, materials,
                        List.copyOf(products), Map.copyOf(probabilities), List.copyOf(skills)));
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read blueprint " + blueprintId, e);
            }
        }
    }

    private static List<TypeQuantity> quantities(Connection connection, String sql, int blueprintId, int activityId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, blueprintId);
            ps.setInt(2, activityId);
            List<TypeQuantity> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new TypeQuantity(rs.getInt(1), rs.getLong(2)));
                }
            }
            return List.copyOf(result);
        }
    }

    public Optional<IndustryActivity> producedBy(int typeId) {
        String sql = """
                SELECT p.blueprint_id, p.activity_id FROM sde_industry_product p
                JOIN sde_industry_type b ON b.type_id = p.blueprint_id
                WHERE p.product_id = ? AND p.activity_id IN (?, ?)
                ORDER BY b.published DESC, p.activity_id, p.blueprint_id LIMIT 1
                """;
        int blueprintId;
        int activityId;
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setInt(1, typeId);
                ps.setInt(2, IndustryActivity.MANUFACTURING);
                ps.setInt(3, IndustryActivity.REACTION);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    blueprintId = rs.getInt(1);
                    activityId = rs.getInt(2);
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to find what makes type " + typeId, e);
            }
        }
        return activity(blueprintId, activityId);
    }

    public Optional<Integer> inventedFrom(int blueprintId) {
        String sql = """
                SELECT p.blueprint_id FROM sde_industry_product p
                JOIN sde_industry_type t ON t.type_id = p.blueprint_id
                WHERE p.product_id = ? AND p.activity_id = ? AND t.category_name = 'Blueprint' AND t.published = 1
                ORDER BY p.blueprint_id LIMIT 1
                """;
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(sql)) {
                ps.setInt(1, blueprintId);
                ps.setInt(2, IndustryActivity.INVENTION);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to find how blueprint " + blueprintId + " is invented", e);
            }
        }
    }

    public Map<Integer, String> names(Collection<Integer> typeIds) {
        Map<Integer, String> names = new HashMap<>();
        synchronized (database) {
            try (PreparedStatement ps = database.connection().prepareStatement(
                    "SELECT name FROM sde_industry_type WHERE type_id = ?")) {
                for (int typeId : typeIds) {
                    ps.setInt(1, typeId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            names.put(typeId, rs.getString(1));
                        }
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read item names", e);
            }
        }
        return names;
    }

    public List<Decryptor> decryptors() {
        synchronized (database) {
            try (Statement statement = database.connection().createStatement();
                 ResultSet rs = statement.executeQuery("""
                         SELECT type_id, name, probability_multiplier, me_modifier, te_modifier, runs_modifier
                         FROM sde_decryptor ORDER BY name
                         """)) {
                List<Decryptor> decryptors = new ArrayList<>();
                while (rs.next()) {
                    decryptors.add(new Decryptor(rs.getInt(1), rs.getString(2), rs.getDouble(3), rs.getInt(4),
                            rs.getInt(5), rs.getInt(6)));
                }
                return decryptors;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to read the decryptors", e);
            }
        }
    }
}
