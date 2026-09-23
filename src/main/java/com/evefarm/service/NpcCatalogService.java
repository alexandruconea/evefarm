package com.evefarm.service;

import com.evefarm.db.dao.ItemTypeDao;
import com.evefarm.db.dao.KillDao;
import com.evefarm.db.dao.NpcTypeDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.esi.FuzzworkApi;
import com.evefarm.model.ItemType;
import com.evefarm.model.NpcType;
import com.evefarm.util.CsvParsing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NpcCatalogService {

    private static final Logger LOG = Logger.getLogger(NpcCatalogService.class.getName());
    private static final Duration MAX_AGE = Duration.ofDays(30);
    private static final String ENTITY_CATEGORY_ID = "11";
    private static final String OFFICER_META_GROUP_ID = "5";

    private final FuzzworkApi fuzzworkApi;
    private final NpcTypeDao npcTypeDao;
    private final ItemTypeDao itemTypeDao;
    private final SettingsDao settingsDao;
    private final KillDao killDao;
    private volatile NpcCatalog cached;

    public NpcCatalogService(FuzzworkApi fuzzworkApi, NpcTypeDao npcTypeDao, ItemTypeDao itemTypeDao,
                             SettingsDao settingsDao, KillDao killDao) {
        this.fuzzworkApi = fuzzworkApi;
        this.npcTypeDao = npcTypeDao;
        this.itemTypeDao = itemTypeDao;
        this.settingsDao = settingsDao;
        this.killDao = killDao;
    }

    public NpcCatalog catalog() {
        NpcCatalog catalog = cached;
        if (catalog == null) {
            catalog = NpcCatalog.of(npcTypeDao.listAll());
            cached = catalog;
        }
        return catalog;
    }

    public Optional<Instant> lastImportedAt() {
        try {
            return settingsDao.get(SettingsDao.NPC_CATALOG_IMPORTED_AT).map(Instant::parse);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void refreshIfStale() {
        boolean stale = catalog().isEmpty() || itemTypeDao.count() == 0 || lastImportedAt()
                .map(at -> at.plus(MAX_AGE).isBefore(Instant.now()))
                .orElse(true);
        if (!stale) {
            return;
        }
        try {
            int count = importCatalog();
            LOG.info("Imported " + count + " NPC types");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to import the NPC catalog - officers can't be recognized until it succeeds", e);
        }
    }

    public int importCatalog() {
        String typesCsv = fuzzworkApi.fetchStaticDataCsv("invTypes.csv");
        List<NpcType> types = parseNpcTypes(fuzzworkApi.fetchStaticDataCsv("invGroups.csv"), typesCsv);
        List<ItemType> items = parseItemTypes(typesCsv, fuzzworkApi.fetchStaticDataCsv("invMetaTypes.csv"));
        if (types.isEmpty() || items.isEmpty()) {
            throw new IllegalStateException("No NPC or item types found in the SDE export - has its format changed?");
        }
        npcTypeDao.replaceAll(types);
        itemTypeDao.replaceAll(items);
        settingsDao.set(SettingsDao.NPC_CATALOG_IMPORTED_AT, Instant.now().toString());
        cached = NpcCatalog.of(types);
        killDao.clearLogProgress();
        return types.size();
    }

    static List<NpcType> parseNpcTypes(String groupsCsv, String typesCsv) {
        Map<Integer, String> entityGroupNames = new HashMap<>();
        List<List<String>> groupRecords = CsvParsing.parseRecords(groupsCsv);
        if (!groupRecords.isEmpty()) {
            Map<String, Integer> column = columnIndex(groupRecords.get(0));
            for (List<String> row : groupRecords.subList(1, groupRecords.size())) {
                Integer groupId = parseInt(field(row, column, "groupID"));
                String name = field(row, column, "groupName");
                if (groupId != null && name != null && ENTITY_CATEGORY_ID.equals(field(row, column, "categoryID"))) {
                    entityGroupNames.put(groupId, name);
                }
            }
        }

        List<NpcType> result = new ArrayList<>();
        List<List<String>> typeRecords = CsvParsing.parseRecords(typesCsv);
        if (!typeRecords.isEmpty()) {
            Map<String, Integer> column = columnIndex(typeRecords.get(0));
            for (List<String> row : typeRecords.subList(1, typeRecords.size())) {
                Integer typeId = parseInt(field(row, column, "typeID"));
                Integer groupId = parseInt(field(row, column, "groupID"));
                String name = field(row, column, "typeName");
                String groupName = groupId == null ? null : entityGroupNames.get(groupId);
                if (typeId != null && name != null && !name.isBlank() && groupName != null) {
                    result.add(new NpcType(typeId, name, groupId, groupName));
                }
            }
        }
        return result;
    }

    static List<ItemType> parseItemTypes(String typesCsv, String metaTypesCsv) {
        Set<Integer> officerTypeIds = new HashSet<>();
        List<List<String>> metaRecords = CsvParsing.parseRecords(metaTypesCsv);
        if (!metaRecords.isEmpty()) {
            Map<String, Integer> column = columnIndex(metaRecords.get(0));
            for (List<String> row : metaRecords.subList(1, metaRecords.size())) {
                Integer typeId = parseInt(field(row, column, "typeID"));
                if (typeId != null && OFFICER_META_GROUP_ID.equals(field(row, column, "metaGroupID"))) {
                    officerTypeIds.add(typeId);
                }
            }
        }

        List<ItemType> result = new ArrayList<>();
        List<List<String>> typeRecords = CsvParsing.parseRecords(typesCsv);
        if (!typeRecords.isEmpty()) {
            Map<String, Integer> column = columnIndex(typeRecords.get(0));
            for (List<String> row : typeRecords.subList(1, typeRecords.size())) {
                Integer typeId = parseInt(field(row, column, "typeID"));
                String name = field(row, column, "typeName");
                String marketGroupId = field(row, column, "marketGroupID");
                boolean onMarket = marketGroupId != null && !marketGroupId.isBlank() && !"None".equals(marketGroupId);
                if (typeId != null && name != null && !name.isBlank() && onMarket
                        && "1".equals(field(row, column, "published"))) {
                    result.add(new ItemType(typeId, name, officerTypeIds.contains(typeId)));
                }
            }
        }
        return result;
    }

    private static Map<String, Integer> columnIndex(List<String> header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            index.put(header.get(i), i);
        }
        return index;
    }

    private static String field(List<String> row, Map<String, Integer> column, String name) {
        Integer index = column.get(name);
        return (index == null || index >= row.size()) ? null : row.get(index);
    }

    private static Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
