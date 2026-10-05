package com.evefarm.service;

import com.evefarm.db.dao.IndustryCatalogDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.esi.FuzzworkApi;
import com.evefarm.esi.SkillCatalogApi;
import com.evefarm.esi.dto.TypeDetailsDto;
import com.evefarm.model.BlueprintChoice;
import com.evefarm.model.Decryptor;
import com.evefarm.model.IndustryActivity;
import com.evefarm.model.IndustryCatalog;
import com.evefarm.model.IndustryType;
import com.evefarm.model.SkillRequirement;
import com.evefarm.model.SolarSystem;
import com.evefarm.model.TypeQuantity;
import com.evefarm.util.CsvTable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public final class IndustryCatalogService {

    private static final Logger LOG = Logger.getLogger(IndustryCatalogService.class.getName());
    private static final Duration MAX_AGE = Duration.ofDays(30);
    private static final int DECRYPTOR_GROUP_ID = 1304;
    private static final int PROBABILITY_MULTIPLIER = 1112;
    private static final int ME_MODIFIER = 1113;
    private static final int TE_MODIFIER = 1114;
    private static final int RUNS_MODIFIER = 1124;
    private static final int FIRST_ABYSSAL_REGION = 12_000_000;

    private final FuzzworkApi fuzzworkApi;
    private final SkillCatalogApi skillCatalogApi;
    private final IndustryCatalogDao industryCatalogDao;
    private final SettingsDao settingsDao;
    private volatile List<BlueprintChoice> choices;
    private volatile List<SolarSystem> solarSystems;

    public IndustryCatalogService(FuzzworkApi fuzzworkApi, SkillCatalogApi skillCatalogApi,
                                  IndustryCatalogDao industryCatalogDao, SettingsDao settingsDao) {
        this.fuzzworkApi = fuzzworkApi;
        this.skillCatalogApi = skillCatalogApi;
        this.industryCatalogDao = industryCatalogDao;
        this.settingsDao = settingsDao;
    }

    public boolean isStale() {
        return industryCatalogDao.countActivities() == 0 || industryCatalogDao.countSolarSystems() == 0
                || lastImportedAt().map(at -> at.plus(MAX_AGE).isBefore(Instant.now())).orElse(true);
    }

    public void refreshIfStale() {
        if (!isStale()) {
            return;
        }
        IndustryCatalog catalog = parse(
                fuzzworkApi.fetchStaticDataCsv("industryActivity.csv"),
                fuzzworkApi.fetchStaticDataCsv("industryActivityMaterials.csv"),
                fuzzworkApi.fetchStaticDataCsv("industryActivityProducts.csv"),
                fuzzworkApi.fetchStaticDataCsv("industryActivityProbabilities.csv"),
                fuzzworkApi.fetchStaticDataCsv("industryActivitySkills.csv"),
                fuzzworkApi.fetchStaticDataCsv("invTypes.csv"),
                fuzzworkApi.fetchStaticDataCsv("invGroups.csv"),
                fuzzworkApi.fetchStaticDataCsv("invCategories.csv"));
        List<SolarSystem> systems = parseSolarSystems(fuzzworkApi.fetchStaticDataCsv("mapSolarSystems.csv"),
                fuzzworkApi.fetchStaticDataCsv("mapRegions.csv"));
        if (catalog.activities().isEmpty() || systems.isEmpty()) {
            throw new IllegalStateException("No blueprints or systems found in the static data - has its format "
                    + "changed?");
        }
        industryCatalogDao.replaceAll(catalog, downloadDecryptors(), systems);
        settingsDao.set(SettingsDao.INDUSTRY_CATALOG_IMPORTED_AT, Instant.now().toString());
        choices = null;
        solarSystems = null;
        LOG.info("Imported " + catalog.activities().size() + " industry activities and " + systems.size()
                + " solar systems");
    }

    public List<BlueprintChoice> manufacturingChoices() {
        List<BlueprintChoice> cached = choices;
        if (cached == null) {
            cached = industryCatalogDao.manufacturingChoices();
            choices = cached;
        }
        return cached;
    }

    public Optional<IndustryActivity> activity(int blueprintId, int activityId) {
        return industryCatalogDao.activity(blueprintId, activityId);
    }

    public Optional<Integer> inventedFrom(int blueprintId) {
        return industryCatalogDao.inventedFrom(blueprintId);
    }

    public Optional<IndustryActivity> producedBy(int typeId) {
        return industryCatalogDao.producedBy(typeId);
    }

    public Map<Integer, String> names(Collection<Integer> typeIds) {
        return industryCatalogDao.names(typeIds);
    }

    public List<Decryptor> decryptors() {
        return industryCatalogDao.decryptors();
    }

    public List<SolarSystem> solarSystems() {
        List<SolarSystem> cached = solarSystems;
        if (cached == null) {
            cached = List.copyOf(industryCatalogDao.solarSystems());
            solarSystems = cached;
        }
        return cached;
    }

    public Optional<SolarSystem> findSystem(String name) {
        String wanted = name == null ? "" : name.strip();
        return solarSystems().stream().filter(system -> system.name().equalsIgnoreCase(wanted)).findFirst();
    }

    public List<SolarSystem> suggestSystems(String text, int limit) {
        return matchingSystems(solarSystems(), text, limit);
    }

    private Optional<Instant> lastImportedAt() {
        try {
            return settingsDao.get(SettingsDao.INDUSTRY_CATALOG_IMPORTED_AT).map(Instant::parse);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private List<Decryptor> downloadDecryptors() {
        List<Integer> types = skillCatalogApi.getGroup(DECRYPTOR_GROUP_ID).types();
        List<Decryptor> decryptors = new ArrayList<>();
        for (int typeId : types == null ? List.<Integer>of() : types) {
            toDecryptor(skillCatalogApi.getType(typeId)).ifPresent(decryptors::add);
        }
        return decryptors;
    }

    static Optional<Decryptor> toDecryptor(TypeDetailsDto type) {
        Map<Integer, Double> attributes = type.attributes();
        Double probability = attributes.get(PROBABILITY_MULTIPLIER);
        if (!Boolean.TRUE.equals(type.published()) || probability == null) {
            return Optional.empty();
        }
        return Optional.of(new Decryptor(type.typeId(), type.name(), probability,
                (int) Math.round(attributes.getOrDefault(ME_MODIFIER, 0.0)),
                (int) Math.round(attributes.getOrDefault(TE_MODIFIER, 0.0)),
                (int) Math.round(attributes.getOrDefault(RUNS_MODIFIER, 0.0))));
    }

    static IndustryCatalog parse(String activityCsv, String materialsCsv, String productsCsv,
                                 String probabilitiesCsv, String skillsCsv, String typesCsv, String groupsCsv,
                                 String categoriesCsv) {
        Map<Long, Builder> builders = new LinkedHashMap<>();
        CsvTable activities = CsvTable.parse(activityCsv);
        for (List<String> row : activities.rows()) {
            Integer blueprint = activities.integer(row, "typeID");
            Integer activity = activities.integer(row, "activityID");
            Long time = activities.number(row, "time");
            if (blueprint != null && activity != null) {
                builders.put(key(blueprint, activity), new Builder(blueprint, activity, time == null ? 0 : time));
            }
        }
        CsvTable materials = CsvTable.parse(materialsCsv);
        for (List<String> row : materials.rows()) {
            Builder builder = builder(builders, materials, row);
            Integer material = materials.integer(row, "materialTypeID");
            Long quantity = materials.number(row, "quantity");
            if (builder != null && material != null && quantity != null) {
                builder.materials.add(new TypeQuantity(material, quantity));
            }
        }
        CsvTable products = CsvTable.parse(productsCsv);
        for (List<String> row : products.rows()) {
            Builder builder = builder(builders, products, row);
            Integer product = products.integer(row, "productTypeID");
            Long quantity = products.number(row, "quantity");
            if (builder != null && product != null && quantity != null) {
                builder.products.add(new TypeQuantity(product, quantity));
            }
        }
        CsvTable probabilities = CsvTable.parse(probabilitiesCsv);
        for (List<String> row : probabilities.rows()) {
            Builder builder = builder(builders, probabilities, row);
            Integer product = probabilities.integer(row, "productTypeID");
            Double probability = probabilities.decimal(row, "probability");
            if (builder != null && product != null && probability != null) {
                builder.probabilities.put(product, probability);
            }
        }
        CsvTable skills = CsvTable.parse(skillsCsv);
        for (List<String> row : skills.rows()) {
            Builder builder = builder(builders, skills, row);
            Integer skill = skills.integer(row, "skillID");
            Integer level = skills.integer(row, "level");
            if (builder != null && skill != null && level != null) {
                builder.skills.add(new SkillRequirement(skill, level));
            }
        }

        Set<Integer> referenced = new HashSet<>();
        List<IndustryActivity> result = new ArrayList<>();
        for (Builder builder : builders.values()) {
            referenced.add(builder.blueprintId);
            builder.materials.forEach(material -> referenced.add(material.typeId()));
            builder.products.forEach(product -> referenced.add(product.typeId()));
            result.add(new IndustryActivity(builder.blueprintId, builder.activityId, builder.time,
                    List.copyOf(builder.materials), List.copyOf(builder.products), Map.copyOf(builder.probabilities),
                    List.copyOf(builder.skills)));
        }
        return new IndustryCatalog(types(referenced, typesCsv, groupsCsv, categoriesCsv), result);
    }

    private static List<IndustryType> types(Set<Integer> referenced, String typesCsv, String groupsCsv,
                                            String categoriesCsv) {
        CsvTable categories = CsvTable.parse(categoriesCsv);
        Map<Integer, String> categoryNames = new HashMap<>();
        for (List<String> row : categories.rows()) {
            Integer id = categories.integer(row, "categoryID");
            if (id != null) {
                categoryNames.put(id, categories.text(row, "categoryName"));
            }
        }
        CsvTable groups = CsvTable.parse(groupsCsv);
        Map<Integer, String[]> groupInfo = new HashMap<>();
        for (List<String> row : groups.rows()) {
            Integer id = groups.integer(row, "groupID");
            Integer category = groups.integer(row, "categoryID");
            if (id != null) {
                groupInfo.put(id, new String[]{groups.text(row, "groupName"),
                        category == null ? null : categoryNames.get(category)});
            }
        }
        CsvTable types = CsvTable.parse(typesCsv);
        List<IndustryType> result = new ArrayList<>();
        for (List<String> row : types.rows()) {
            Integer id = types.integer(row, "typeID");
            String name = types.text(row, "typeName");
            if (id == null || name == null || !referenced.contains(id)) {
                continue;
            }
            Integer group = types.integer(row, "groupID");
            String[] info = group == null ? null : groupInfo.get(group);
            Integer published = types.integer(row, "published");
            result.add(new IndustryType(id, name, info == null ? null : info[0], info == null ? null : info[1],
                    published != null && published == 1));
        }
        return result;
    }

    static List<SolarSystem> matchingSystems(List<SolarSystem> systems, String text, int limit) {
        String wanted = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<SolarSystem> starting = new ArrayList<>();
        List<SolarSystem> containing = new ArrayList<>();
        for (SolarSystem system : systems) {
            String name = system.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(wanted)) {
                starting.add(system);
            } else if (name.contains(wanted)) {
                containing.add(system);
            }
        }
        starting.sort(Comparator.comparingInt((SolarSystem system) -> system.name().length())
                .thenComparing(SolarSystem::name, String.CASE_INSENSITIVE_ORDER));
        starting.addAll(containing);
        return List.copyOf(starting.subList(0, Math.min(limit, starting.size())));
    }

    static List<SolarSystem> parseSolarSystems(String systemsCsv, String regionsCsv) {
        CsvTable regions = CsvTable.parse(regionsCsv);
        Map<Integer, String> regionNames = new HashMap<>();
        for (List<String> row : regions.rows()) {
            Integer id = regions.integer(row, "regionID");
            if (id != null) {
                regionNames.put(id, regions.text(row, "regionName"));
            }
        }
        CsvTable systems = CsvTable.parse(systemsCsv);
        List<SolarSystem> result = new ArrayList<>();
        for (List<String> row : systems.rows()) {
            Long id = systems.number(row, "solarSystemID");
            String name = systems.text(row, "solarSystemName");
            Double security = systems.decimal(row, "security");
            Integer region = systems.integer(row, "regionID");
            if (id != null && name != null && security != null && region != null && region < FIRST_ABYSSAL_REGION) {
                result.add(new SolarSystem(id, name, security, regionNames.get(region)));
            }
        }
        result.sort(Comparator.comparing(SolarSystem::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static Builder builder(Map<Long, Builder> builders, CsvTable table, List<String> row) {
        Integer blueprint = table.integer(row, "typeID");
        Integer activity = table.integer(row, "activityID");
        return blueprint == null || activity == null ? null : builders.get(key(blueprint, activity));
    }

    private static long key(int blueprintId, int activityId) {
        return (long) blueprintId * 100 + activityId;
    }

    private static final class Builder {

        private final int blueprintId;
        private final int activityId;
        private final long time;
        private final List<TypeQuantity> materials = new ArrayList<>();
        private final List<TypeQuantity> products = new ArrayList<>();
        private final Map<Integer, Double> probabilities = new HashMap<>();
        private final List<SkillRequirement> skills = new ArrayList<>();

        private Builder(int blueprintId, int activityId, long time) {
            this.blueprintId = blueprintId;
            this.activityId = activityId;
            this.time = time;
        }
    }
}
