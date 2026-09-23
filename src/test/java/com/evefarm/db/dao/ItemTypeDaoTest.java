package com.evefarm.db.dao;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.model.ItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemTypeDaoTest {

    private ItemTypeDao dao;

    @BeforeEach
    void setUp() {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        dao = new ItemTypeDao(database);
        dao.replaceAll(List.of(
                new ItemType(1, "Large Shield Booster II", false),
                new ItemType(2, "Estamel's Modified Large Shield Booster", true),
                new ItemType(3, "Tobias' Modified Large Shield Booster", true),
                new ItemType(4, "Estamel's Modified 350mm Railgun", true)));
    }

    @Test
    void searchIsCaseInsensitiveAndListsOfficerModulesFirst() {
        assertEquals(List.of("Estamel's Modified Large Shield Booster", "Tobias' Modified Large Shield Booster",
                        "Large Shield Booster II"),
                dao.search("large SHIELD", 10).stream().map(ItemType::typeName).toList());
    }

    @Test
    void anOfficersOwnModulesAreFoundByPrefix() {
        assertEquals(List.of("Estamel's Modified 350mm Railgun", "Estamel's Modified Large Shield Booster"),
                dao.listOfficerItems("Estamel").stream().map(ItemType::typeName).toList());
        assertEquals(4, dao.count());
    }

    @Test
    void likeWildcardsInTheSearchTextAreLiteral() {
        assertEquals(List.of(), dao.search("%", 10));
        assertEquals(List.of(), dao.search("_", 10));
    }
}
