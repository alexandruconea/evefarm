package com.evefarm.service;

import com.evefarm.db.dao.ItemTypeDao;
import com.evefarm.db.dao.KillDao;
import com.evefarm.db.dao.NpcTypeDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.esi.FuzzworkApi;
import com.evefarm.model.ItemType;
import com.evefarm.model.NpcType;
import com.evefarm.util.CsvTable;

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
        CsvTable groups = CsvTable.parse(groupsCsv);
        for (List<String> row : groups.rows()) {
            Integer groupId = groups.integer(row, "groupID");
            String name = groups.text(row, "groupName");
            if (groupId != null && name != null && ENTITY_CATEGORY_ID.equals(groups.text(row, "categoryID"))) {
                entityGroupNames.put(groupId, name);
            }
        }

        List<NpcType> result = new ArrayList<>();
        CsvTable types = CsvTable.parse(typesCsv);
        for (List<String> row : types.rows()) {
            Integer typeId = types.integer(row, "typeID");
            Integer groupId = types.integer(row, "groupID");
            String name = types.text(row, "typeName");
            String groupName = groupId == null ? null : entityGroupNames.get(groupId);
            if (typeId != null && name != null && groupName != null) {
                result.add(new NpcType(typeId, name, groupId, groupName));
            }
        }
        return result;
    }

    static List<ItemType> parseItemTypes(String typesCsv, String metaTypesCsv) {
        Set<Integer> officerTypeIds = new HashSet<>();
        CsvTable metaTypes = CsvTable.parse(metaTypesCsv);
        for (List<String> row : metaTypes.rows()) {
            Integer typeId = metaTypes.integer(row, "typeID");
            if (typeId != null && OFFICER_META_GROUP_ID.equals(metaTypes.text(row, "metaGroupID"))) {
                officerTypeIds.add(typeId);
            }
        }

        List<ItemType> result = new ArrayList<>();
        CsvTable types = CsvTable.parse(typesCsv);
        for (List<String> row : types.rows()) {
            Integer typeId = types.integer(row, "typeID");
            String name = types.text(row, "typeName");
            boolean onMarket = types.text(row, "marketGroupID") != null;
            if (typeId != null && name != null && onMarket && "1".equals(types.text(row, "published"))) {
                result.add(new ItemType(typeId, name, officerTypeIds.contains(typeId)));
            }
        }
        return result;
    }
}
