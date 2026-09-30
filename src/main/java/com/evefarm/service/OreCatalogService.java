package com.evefarm.service;

import com.evefarm.db.dao.OreDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.esi.FuzzworkApi;
import com.evefarm.model.Ore;
import com.evefarm.util.CsvTable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OreCatalogService {

    private static final Logger LOG = Logger.getLogger(OreCatalogService.class.getName());
    private static final Duration MAX_AGE = Duration.ofDays(30);
    private static final int ASTEROID_CATEGORY_ID = 25;
    private static final Set<Integer> GAS_GROUP_IDS = Set.of(711, 4168);
    private static final String COMPRESSED_PREFIX = "Compressed ";
    private static final String BATCH_COMPRESSED_PREFIX = "Batch Compressed ";

    private final FuzzworkApi fuzzworkApi;
    private final OreDao oreDao;
    private final SettingsDao settingsDao;
    private volatile Map<Integer, Ore> cached;

    public OreCatalogService(FuzzworkApi fuzzworkApi, OreDao oreDao, SettingsDao settingsDao) {
        this.fuzzworkApi = fuzzworkApi;
        this.oreDao = oreDao;
        this.settingsDao = settingsDao;
    }

    public Map<Integer, Ore> ores() {
        Map<Integer, Ore> ores = cached;
        if (ores == null) {
            ores = oreDao.loadAll();
            cached = ores;
        }
        return ores;
    }

    public void refreshIfStale() {
        boolean stale = oreDao.count() == 0 || lastImportedAt()
                .map(at -> at.plus(MAX_AGE).isBefore(Instant.now()))
                .orElse(true);
        if (!stale) {
            return;
        }
        try {
            List<Ore> ores = parseOres(fuzzworkApi.fetchStaticDataCsv("invGroups.csv"),
                    fuzzworkApi.fetchStaticDataCsv("invTypes.csv"),
                    fuzzworkApi.fetchStaticDataCsv("invTypeMaterials.csv"));
            if (ores.isEmpty()) {
                throw new IllegalStateException("No ores found in the SDE export - has its format changed?");
            }
            oreDao.replaceAll(ores);
            settingsDao.set(SettingsDao.ORE_CATALOG_IMPORTED_AT, Instant.now().toString());
            cached = null;
            LOG.info("Imported " + ores.size() + " ore types");
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Failed to import the ore catalog - compressed and refined values need it", e);
        }
    }

    private Optional<Instant> lastImportedAt() {
        try {
            return settingsDao.get(SettingsDao.ORE_CATALOG_IMPORTED_AT).map(Instant::parse);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static List<Ore> parseOres(String groupsCsv, String typesCsv, String materialsCsv) {
        CsvTable groups = CsvTable.parse(groupsCsv);
        Set<Integer> asteroidGroups = new HashSet<>();
        for (List<String> row : groups.rows()) {
            Integer groupId = groups.integer(row, "groupID");
            Integer categoryId = groups.integer(row, "categoryID");
            if (groupId != null && (GAS_GROUP_IDS.contains(groupId)
                    || categoryId != null && categoryId == ASTEROID_CATEGORY_ID)) {
                asteroidGroups.add(groupId);
            }
        }

        CsvTable types = CsvTable.parse(typesCsv);
        Map<Integer, String> names = new LinkedHashMap<>();
        Map<Integer, Integer> portions = new HashMap<>();
        Map<String, Integer> idsByName = new HashMap<>();
        for (List<String> row : types.rows()) {
            Integer typeId = types.integer(row, "typeID");
            Integer groupId = types.integer(row, "groupID");
            String name = types.text(row, "typeName");
            Integer published = types.integer(row, "published");
            if (typeId == null || groupId == null || name == null || !asteroidGroups.contains(groupId)
                    || published == null || published != 1) {
                continue;
            }
            Integer portion = types.integer(row, "portionSize");
            names.put(typeId, name);
            portions.put(typeId, portion == null || portion < 1 ? 1 : portion);
            idsByName.put(name, typeId);
        }

        CsvTable materialTable = CsvTable.parse(materialsCsv);
        Map<Integer, Map<Integer, Long>> materials = new HashMap<>();
        for (List<String> row : materialTable.rows()) {
            Integer typeId = materialTable.integer(row, "typeID");
            Integer materialId = materialTable.integer(row, "materialTypeID");
            Long quantity = materialTable.number(row, "quantity");
            if (typeId != null && materialId != null && quantity != null && quantity > 0 && names.containsKey(typeId)) {
                materials.computeIfAbsent(typeId, ignored -> new LinkedHashMap<>()).put(materialId, quantity);
            }
        }

        List<Ore> ores = new ArrayList<>();
        for (Map.Entry<Integer, String> type : names.entrySet()) {
            String name = type.getValue();
            if (name.startsWith(COMPRESSED_PREFIX) || name.startsWith(BATCH_COMPRESSED_PREFIX)) {
                continue;
            }
            ores.add(new Ore(type.getKey(), portions.get(type.getKey()), idsByName.get(COMPRESSED_PREFIX + name),
                    materials.getOrDefault(type.getKey(), Map.of())));
        }
        return ores;
    }
}
