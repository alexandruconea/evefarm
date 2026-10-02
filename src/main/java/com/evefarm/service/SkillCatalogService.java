package com.evefarm.service;

import com.evefarm.db.dao.AcceleratorDao;
import com.evefarm.db.dao.ImplantBonusDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.SkillCatalogDao;
import com.evefarm.esi.SkillCatalogApi;
import com.evefarm.esi.dto.TypeDetailsDto;
import com.evefarm.esi.dto.TypeGroupDto;
import com.evefarm.model.Accelerator;
import com.evefarm.model.CharacterAttributes;
import com.evefarm.model.SkillInfo;
import com.evefarm.model.SkillRequirement;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

public final class SkillCatalogService {

    private static final Logger LOG = Logger.getLogger(SkillCatalogService.class.getName());
    private static final Duration MAX_AGE = Duration.ofDays(30);
    private static final int SKILL_CATEGORY_ID = 16;
    private static final int BOOSTER_GROUP_ID = 303;
    private static final int BOOSTER_DURATION = 330;
    private static final String ACCELERATOR_NAME = "cerebral accelerator";
    private static final String EXPIRED_PREFIX = "expired";
    private static final int DOWNLOAD_THREADS = 8;
    private static final int SKILL_RANK = 275;
    private static final int PRIMARY_ATTRIBUTE = 180;
    private static final int SECONDARY_ATTRIBUTE = 181;
    private static final int[][] REQUIRED_SKILLS = {
            {182, 277}, {183, 278}, {184, 279}, {1285, 1286}, {1289, 1287}, {1290, 1288}};
    private static final Map<Integer, String> ATTRIBUTE_NAMES = Map.of(
            164, CharacterAttributes.CHARISMA,
            165, CharacterAttributes.INTELLIGENCE,
            166, CharacterAttributes.MEMORY,
            167, CharacterAttributes.PERCEPTION,
            168, CharacterAttributes.WILLPOWER);

    private final SkillCatalogApi api;
    private final SkillCatalogDao skillCatalogDao;
    private final ImplantBonusDao implantBonusDao;
    private final AcceleratorDao acceleratorDao;
    private final SettingsDao settingsDao;
    private volatile Map<Integer, SkillInfo> cached;

    public SkillCatalogService(SkillCatalogApi api, SkillCatalogDao skillCatalogDao, ImplantBonusDao implantBonusDao,
                               AcceleratorDao acceleratorDao, SettingsDao settingsDao) {
        this.api = api;
        this.skillCatalogDao = skillCatalogDao;
        this.implantBonusDao = implantBonusDao;
        this.acceleratorDao = acceleratorDao;
        this.settingsDao = settingsDao;
    }

    public Map<Integer, SkillInfo> catalog() {
        Map<Integer, SkillInfo> skills = cached;
        if (skills == null) {
            skills = skillCatalogDao.loadAll();
            cached = skills;
        }
        return skills;
    }

    public void refreshIfStale() {
        boolean stale = catalog().isEmpty() || lastImportedAt()
                .map(at -> at.plus(MAX_AGE).isBefore(Instant.now()))
                .orElse(true);
        if (!stale) {
            return;
        }
        List<SkillInfo> skills = download();
        if (skills.isEmpty()) {
            throw new IllegalStateException("EVE returned no skills");
        }
        skillCatalogDao.replaceAll(skills);
        settingsDao.set(SettingsDao.SKILL_CATALOG_IMPORTED_AT, Instant.now().toString());
        cached = null;
        LOG.info("Imported " + skills.size() + " skills");
    }

    private Optional<Instant> lastImportedAt() {
        return importedAt(SettingsDao.SKILL_CATALOG_IMPORTED_AT);
    }

    private Optional<Instant> importedAt(String key) {
        try {
            return settingsDao.get(key).map(Instant::parse);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<Accelerator> accelerators() {
        boolean stale = importedAt(SettingsDao.ACCELERATOR_CATALOG_IMPORTED_AT)
                .map(at -> at.plus(MAX_AGE).isBefore(Instant.now()))
                .orElse(true);
        if (stale) {
            List<Accelerator> accelerators = downloadAccelerators();
            acceleratorDao.replaceCatalog(accelerators);
            settingsDao.set(SettingsDao.ACCELERATOR_CATALOG_IMPORTED_AT, Instant.now().toString());
            LOG.info("Imported " + accelerators.size() + " cerebral accelerators");
            return accelerators;
        }
        return acceleratorDao.catalog();
    }

    private List<Accelerator> downloadAccelerators() {
        List<Integer> boosters = api.getGroup(BOOSTER_GROUP_ID).types();
        List<Integer> candidates = api.resolveNames(boosters == null ? List.of() : boosters).entrySet().stream()
                .filter(entry -> isUsableAccelerator(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        ExecutorService pool = Executors.newFixedThreadPool(DOWNLOAD_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "accelerator-catalog");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<TypeDetailsDto>> types = new ArrayList<>();
            for (int typeId : candidates) {
                types.add(pool.submit(() -> api.getType(typeId)));
            }
            List<Accelerator> accelerators = new ArrayList<>();
            for (Future<TypeDetailsDto> future : types) {
                toAccelerator(await(future)).ifPresent(accelerators::add);
            }
            return accelerators;
        } finally {
            pool.shutdownNow();
        }
    }

    static boolean isUsableAccelerator(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return lower.contains(ACCELERATOR_NAME) && !lower.startsWith(EXPIRED_PREFIX);
    }

    static Optional<Accelerator> toAccelerator(TypeDetailsDto type) {
        if (!Boolean.TRUE.equals(type.published())) {
            return Optional.empty();
        }
        Map<Integer, Double> attributes = type.attributes();
        CharacterAttributes bonus = bonusOf(attributes);
        boolean even = bonus.charisma() > 0 && bonus.equals(CharacterAttributes.all(bonus.charisma()));
        double hours = attributes.getOrDefault(BOOSTER_DURATION, 0.0) / 3_600_000;
        if (!even || hours <= 0) {
            return Optional.empty();
        }
        return Optional.of(new Accelerator(type.typeId(), type.name(), bonus.charisma(), hours));
    }

    private List<SkillInfo> download() {
        ExecutorService pool = Executors.newFixedThreadPool(DOWNLOAD_THREADS, runnable -> {
            Thread thread = new Thread(runnable, "skill-catalog");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<TypeGroupDto>> groups = new ArrayList<>();
            for (int groupId : api.listGroupIds(SKILL_CATEGORY_ID)) {
                groups.add(pool.submit(() -> api.getGroup(groupId)));
            }
            Map<Integer, String> groupOfType = new LinkedHashMap<>();
            for (Future<TypeGroupDto> future : groups) {
                TypeGroupDto group = await(future);
                if (Boolean.TRUE.equals(group.published()) && group.types() != null) {
                    group.types().forEach(typeId -> groupOfType.put(typeId, group.name()));
                }
            }
            List<Future<TypeDetailsDto>> types = new ArrayList<>();
            for (int typeId : groupOfType.keySet()) {
                types.add(pool.submit(() -> api.getType(typeId)));
            }
            List<SkillInfo> skills = new ArrayList<>();
            for (Future<TypeDetailsDto> future : types) {
                TypeDetailsDto type = await(future);
                if (Boolean.TRUE.equals(type.published())) {
                    skills.add(toSkill(type, groupOfType.get(type.typeId())));
                }
            }
            return skills;
        } finally {
            pool.shutdownNow();
        }
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading the skill list", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Couldn't download the skill list from EVE", e.getCause());
        }
    }

    static SkillInfo toSkill(TypeDetailsDto type, String groupName) {
        Map<Integer, Double> attributes = type.attributes();
        int rank = (int) Math.round(attributes.getOrDefault(SKILL_RANK, 1.0));
        return new SkillInfo(type.typeId(), type.name(), groupName, plainText(type.description()), Math.max(1, rank),
                attributeName(attributes.get(PRIMARY_ATTRIBUTE)), attributeName(attributes.get(SECONDARY_ATTRIBUTE)),
                requirementsOf(attributes));
    }

    static List<SkillRequirement> requirementsOf(Map<Integer, Double> attributes) {
        List<SkillRequirement> requirements = new ArrayList<>();
        for (int[] pair : REQUIRED_SKILLS) {
            Double skill = attributes.get(pair[0]);
            if (skill != null && skill > 0) {
                int level = (int) Math.round(attributes.getOrDefault(pair[1], 1.0));
                requirements.add(new SkillRequirement((int) Math.round(skill),
                        Math.max(1, Math.min(SkillPlanner.MAX_LEVEL, level))));
            }
        }
        return requirements;
    }

    static CharacterAttributes bonusOf(Map<Integer, Double> attributes) {
        return new CharacterAttributes(bonus(attributes, 175), bonus(attributes, 176), bonus(attributes, 177),
                bonus(attributes, 178), bonus(attributes, 179));
    }

    private static int bonus(Map<Integer, Double> attributes, int attributeId) {
        return (int) Math.round(attributes.getOrDefault(attributeId, 0.0));
    }

    private static String attributeName(Double attributeId) {
        return attributeId == null ? null : ATTRIBUTE_NAMES.get((int) Math.round(attributeId));
    }

    static String plainText(String description) {
        if (description == null) {
            return "";
        }
        return description.replaceAll("<br\\s*/?>", "\n").replaceAll("<[^>]+>", "").strip();
    }

    public CharacterAttributes implantBonus(List<Integer> implantIds) {
        CharacterAttributes total = CharacterAttributes.NONE;
        for (int implantId : implantIds) {
            CharacterAttributes bonus = implantBonusDao.find(implantId).orElseGet(() -> {
                CharacterAttributes fetched = bonusOf(api.getType(implantId).attributes());
                implantBonusDao.save(implantId, fetched);
                return fetched;
            });
            total = total.plus(bonus);
        }
        return total;
    }

    public record ItemRequirements(List<SkillRequirement> requirements, List<String> unknownNames) {
    }

    public ItemRequirements requirementsOfItems(List<String> names) {
        Map<String, Integer> ids = api.resolveTypeIds(names);
        List<SkillRequirement> requirements = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String name : names) {
            Integer typeId = ids.get(name.toLowerCase(Locale.ROOT));
            if (typeId == null) {
                unknown.add(name);
            } else {
                requirements.addAll(requirementsOf(api.getType(typeId).attributes()));
            }
        }
        return new ItemRequirements(requirements, unknown);
    }
}
