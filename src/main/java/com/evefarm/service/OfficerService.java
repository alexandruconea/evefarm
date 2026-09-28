package com.evefarm.service;

import com.evefarm.db.dao.EncounterDao;
import com.evefarm.db.dao.ItemTypeDao;
import com.evefarm.db.dao.OfficerDao;
import com.evefarm.db.dao.WalletJournalDao;
import com.evefarm.esi.UniverseApi;
import com.evefarm.model.CharacterContribution;
import com.evefarm.model.EncounterSummary;
import com.evefarm.model.ItemType;
import com.evefarm.model.JournalPayout;
import com.evefarm.model.OfficerDrop;
import com.evefarm.model.OfficerSighting;
import com.evefarm.model.ParsedEncounter;
import com.evefarm.model.SpawnClass;
import com.evefarm.model.SpawnMember;
import com.evefarm.model.SpawnRow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class OfficerService {

    private static final Duration PAYOUT_WINDOW = Duration.ofMinutes(25);
    private static final Pattern REASON_ENTRY = Pattern.compile("(\\d+)\\s*:\\s*(\\d+)");

    private static final Logger LOG = Logger.getLogger(OfficerService.class.getName());
    private static final int ITEM_SEARCH_LIMIT = 200;

    private final EncounterDao encounterDao;
    private final OfficerDao officerDao;
    private final WalletJournalDao walletJournalDao;
    private final ItemTypeDao itemTypeDao;
    private final NpcCatalogService npcCatalogService;
    private final PriceService priceService;
    private final UniverseApi universeApi;

    public OfficerService(EncounterDao encounterDao, OfficerDao officerDao, WalletJournalDao walletJournalDao,
                          ItemTypeDao itemTypeDao, NpcCatalogService npcCatalogService, PriceService priceService,
                          UniverseApi universeApi) {
        this.encounterDao = encounterDao;
        this.officerDao = officerDao;
        this.walletJournalDao = walletJournalDao;
        this.itemTypeDao = itemTypeDao;
        this.npcCatalogService = npcCatalogService;
        this.priceService = priceService;
        this.universeApi = universeApi;
    }

    public NpcCatalog catalog() {
        return npcCatalogService.catalog();
    }

    public List<OfficerSighting> listSightings() {
        NpcCatalog catalog = npcCatalogService.catalog();
        List<OfficerSighting> result = new ArrayList<>();
        for (OfficerSighting sighting : encounterDao.listOfficerSightings(catalog.officerNames())) {
            OfficerSighting enriched = sighting.withOfficerGroup(
                    catalog.find(sighting.officerName()).map(NpcCatalog.Entry::groupName).orElse(null));
            if (enriched.payout() == null && enriched.killed()) {
                Optional<JournalPayout> payout = findPayout(enriched, catalog);
                if (payout.isPresent()) {
                    officerDao.savePayout(enriched.characterId(), enriched.officerName(), enriched.firstSeenAt(),
                            payout.get());
                    enriched = enriched.withPayout(payout.get());
                }
            }
            result.add(enriched);
        }
        return OfficerSightingGrouper.group(result);
    }

    private Optional<JournalPayout> findPayout(OfficerSighting sighting, NpcCatalog catalog) {
        Set<Integer> officerTypeIds = new HashSet<>(catalog.find(sighting.officerName())
                .map(NpcCatalog.Entry::typeIds).orElse(List.of()));
        List<JournalPayout> payouts = walletJournalDao.findBountyPayouts(sighting.characterId(),
                sighting.killedAt(), sighting.killedAt().plus(PAYOUT_WINDOW));
        for (JournalPayout payout : payouts) {
            if (parseReason(payout.reason()).keySet().stream().anyMatch(officerTypeIds::contains)) {
                return Optional.of(payout);
            }
        }
        return Optional.empty();
    }

    static Map<Integer, Integer> parseReason(String reason) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        if (reason == null) {
            return counts;
        }
        Matcher entry = REASON_ENTRY.matcher(reason);
        while (entry.find()) {
            counts.merge(Integer.parseInt(entry.group(1)), Integer.parseInt(entry.group(2)), Integer::sum);
        }
        return counts;
    }

    public String describePayoutNpcs(JournalPayout payout) {
        NpcCatalog catalog = npcCatalogService.catalog();
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : parseReason(payout.reason()).entrySet()) {
            String name = catalog.nameForTypeId(entry.getKey()).orElse("type #" + entry.getKey());
            parts.add(entry.getValue() + "× " + name);
        }
        return String.join(", ", parts);
    }

    public List<SpawnRow> listAllSpawns() {
        NpcCatalog catalog = npcCatalogService.catalog();
        List<SpawnRow> rows = new ArrayList<>();
        for (List<EncounterSummary> fight : groupFights(encounterDao.listEncounterSummaries())) {
            List<ParsedEncounter.Npc> npcs = mergeNpcs(fight.stream().flatMap(e -> e.npcs().stream()).toList());
            int killed = 0;
            double bounty = 0;
            SpawnClass strongest = SpawnClass.OTHER;
            boolean missions = false;
            List<ParsedEncounter.Npc> killedNpcs = new ArrayList<>();
            for (ParsedEncounter.Npc npc : npcs) {
                killed += npc.kills();
                bounty += npc.bounty();
                SpawnClass spawnClass = catalog.spawnClassOf(npc.name());
                if (spawnClass.ordinal() > strongest.ordinal()) {
                    strongest = spawnClass;
                }
                if (catalog.factionLabelFor(npc.name()).filter(NpcCatalog.MISSIONS_LABEL::equals).isPresent()) {
                    missions = true;
                }
                if (npc.kills() > 0) {
                    killedNpcs.add(npc);
                }
            }
            if (killed == 0) {
                continue;
            }
            killedNpcs.sort(Comparator.comparingInt(ParsedEncounter.Npc::kills).reversed()
                    .thenComparing(ParsedEncounter.Npc::name));
            String composition = killedNpcs.stream()
                    .map(npc -> npc.kills() + "× " + npc.name())
                    .collect(Collectors.joining(", "));
            String kind = strongest != SpawnClass.OTHER ? strongest.toString()
                    : missions ? NpcCatalog.MISSIONS_LABEL : "Other";
            List<CharacterContribution> contributions = contributions(fight);
            rows.add(new SpawnRow(fight.stream().map(EncounterSummary::encounterId).toList(),
                    contributions.stream().map(CharacterContribution::characterName)
                            .collect(Collectors.joining(", ")),
                    fight.stream().map(EncounterSummary::startedAt).min(Comparator.naturalOrder()).orElseThrow(),
                    fight.stream().map(EncounterSummary::endedAt).max(Comparator.naturalOrder()).orElseThrow(),
                    fight.get(0).solarSystem(), kind, killed, bounty, composition, contributions));
        }
        rows.sort(Comparator.comparing(SpawnRow::startedAt).reversed());
        return rows;
    }

    static List<List<EncounterSummary>> groupFights(List<EncounterSummary> encounters) {
        List<EncounterSummary> sorted = new ArrayList<>(encounters);
        sorted.sort(Comparator.comparing(EncounterSummary::startedAt).thenComparing(EncounterSummary::encounterId));
        List<List<EncounterSummary>> fights = new ArrayList<>();
        for (EncounterSummary encounter : sorted) {
            List<EncounterSummary> target = null;
            for (int i = fights.size() - 1; i >= 0 && target == null; i--) {
                if (sameFight(encounter, fights.get(i))) {
                    target = fights.get(i);
                }
            }
            if (target == null) {
                target = new ArrayList<>();
                fights.add(target);
            }
            target.add(encounter);
        }
        return fights;
    }

    private static boolean sameFight(EncounterSummary encounter, List<EncounterSummary> fight) {
        EncounterSummary first = fight.get(0);
        if (encounter.solarSystem() == null || !encounter.solarSystem().equals(first.solarSystem())) {
            return false;
        }
        Instant fightEnd = fight.stream().map(EncounterSummary::endedAt).max(Comparator.naturalOrder())
                .orElse(first.endedAt());
        if (encounter.startedAt().isAfter(fightEnd.plus(GameLogKillParser.ENCOUNTER_GAP))) {
            return false;
        }
        Set<String> fightNpcs = new HashSet<>();
        fight.forEach(member -> member.npcs().stream().filter(OfficerService::engaged)
                .forEach(npc -> fightNpcs.add(npc.name())));
        return encounter.npcs().stream().filter(OfficerService::engaged)
                .anyMatch(npc -> fightNpcs.contains(npc.name()));
    }

    private static boolean engaged(ParsedEncounter.Npc npc) {
        return npc.kills() > 0 || npc.damageDealt() > 0 || npc.damageTaken() > 0;
    }

    private static List<CharacterContribution> contributions(List<EncounterSummary> fight) {
        Map<String, CharacterContribution> byName = new LinkedHashMap<>();
        for (EncounterSummary encounter : fight) {
            int kills = 0;
            double bounty = 0;
            long damage = 0;
            for (ParsedEncounter.Npc npc : encounter.npcs()) {
                kills += npc.kills();
                bounty += npc.bounty();
                damage += npc.damageDealt();
            }
            byName.merge(encounter.characterName(),
                    new CharacterContribution(encounter.characterName(), kills, bounty, damage),
                    (a, b) -> new CharacterContribution(a.characterName(), a.kills() + b.kills(),
                            a.bounty() + b.bounty(), a.damageDealt() + b.damageDealt()));
        }
        List<CharacterContribution> result = new ArrayList<>(byName.values());
        result.sort(Comparator.comparingInt(CharacterContribution::kills).reversed()
                .thenComparing(Comparator.comparingLong(CharacterContribution::damageDealt).reversed()));
        return result;
    }

    static List<ParsedEncounter.Npc> mergeNpcs(List<ParsedEncounter.Npc> npcs) {
        Map<String, ParsedEncounter.Npc> byName = new LinkedHashMap<>();
        for (ParsedEncounter.Npc npc : npcs) {
            byName.merge(npc.name(), npc, (a, b) -> new ParsedEncounter.Npc(a.name(),
                    earlier(a.firstSeenAt(), b.firstSeenAt()),
                    later(a.lastSeenAt(), b.lastSeenAt()),
                    a.kills() + b.kills(),
                    a.bounty() + b.bounty(),
                    later(a.lastKillAt(), b.lastKillAt()),
                    a.damageDealt() + b.damageDealt(),
                    a.damageTaken() + b.damageTaken()));
        }
        return new ArrayList<>(byName.values());
    }

    private static Instant earlier(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        return b == null || a.isBefore(b) ? a : b;
    }

    private static Instant later(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        return b == null || a.isAfter(b) ? a : b;
    }

    public List<SpawnMember> listSpawn(List<Long> encounterIds) {
        NpcCatalog catalog = npcCatalogService.catalog();
        List<ParsedEncounter.Npc> npcs = new ArrayList<>();
        for (long encounterId : new LinkedHashSet<>(encounterIds)) {
            npcs.addAll(encounterDao.listEncounterNpcs(encounterId));
        }
        List<SpawnMember> members = new ArrayList<>();
        for (ParsedEncounter.Npc npc : mergeNpcs(npcs)) {
            Optional<NpcCatalog.Entry> entry = catalog.find(npc.name());
            members.add(new SpawnMember(npc, entry.map(NpcCatalog.Entry::groupName).orElse(null),
                    entry.map(NpcCatalog.Entry::spawnClass).orElse(SpawnClass.OTHER)));
        }
        members.sort(Comparator.comparing((SpawnMember m) -> m.spawnClass().ordinal()).reversed()
                .thenComparing(m -> m.npc().firstSeenAt()));
        return members;
    }

    public List<String> listBelts(String solarSystem) {
        if (solarSystem == null || solarSystem.isBlank()) {
            return List.of();
        }
        List<String> cached = officerDao.listBelts(solarSystem);
        if (!cached.isEmpty()) {
            return cached;
        }
        Optional<Long> systemId = universeApi.resolveSolarSystemId(solarSystem);
        if (systemId.isEmpty()) {
            return List.of();
        }
        Map<Long, String> belts = new LinkedHashMap<>();
        for (long beltId : universeApi.getAsteroidBeltIds(systemId.get())) {
            belts.put(beltId, universeApi.getAsteroidBeltName(beltId));
        }
        officerDao.saveBelts(solarSystem, belts);
        return officerDao.listBelts(solarSystem);
    }

    public List<OfficerDrop> listDrops(OfficerSighting sighting) {
        List<OfficerDrop> drops = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (OfficerSighting.Member member : sighting.members()) {
            if (keys.add(member.characterId() + "|" + member.firstSeenAt())) {
                drops.addAll(officerDao.listDrops(member.characterId(), sighting.officerName(), member.firstSeenAt()));
            }
        }
        drops.sort(Comparator.comparingDouble(OfficerDrop::totalValue).reversed()
                .thenComparing(OfficerDrop::typeName));
        return drops;
    }

    public void addDrop(OfficerSighting sighting, ItemType item, int quantity, double unitPrice) {
        officerDao.addDrop(sighting.characterId(), sighting.officerName(), sighting.firstSeenAt(), item, quantity,
                unitPrice);
    }

    public void removeDrop(OfficerDrop drop) {
        officerDao.removeDrop(drop.id());
    }

    public List<ItemType> suggestedDrops(String officerName) {
        List<ItemType> own = itemTypeDao.listOfficerItems(officerItemPrefix(officerName));
        return own.isEmpty() ? itemTypeDao.listOfficerItems("") : own;
    }

    public List<ItemType> searchItems(String text) {
        return itemTypeDao.search(text.trim(), ITEM_SEARCH_LIMIT);
    }

    static String officerItemPrefix(String officerName) {
        if (officerName.startsWith("Unit ")) {
            return officerName;
        }
        int space = officerName.indexOf(' ');
        return space < 0 ? officerName : officerName.substring(0, space);
    }

    public OptionalDouble currentPrice(int typeId) {
        try {
            priceService.ensureFreshPrices(List.of(typeId));
        } catch (Exception e) {
            LOG.log(Level.INFO, "Couldn't fetch a price for type " + typeId, e);
        }
        OptionalDouble price = priceService.getUnitPrice(typeId);
        return price.isPresent() && price.getAsDouble() > 0 ? price : OptionalDouble.empty();
    }

    public void saveDetails(OfficerSighting sighting, String belt, String notes) {
        Set<String> keys = new HashSet<>();
        for (OfficerSighting.Member member : sighting.members()) {
            if (keys.add(member.characterId() + "|" + member.firstSeenAt())) {
                officerDao.saveDetails(member.characterId(), sighting.officerName(), member.firstSeenAt(),
                        blankToNull(belt), blankToNull(notes));
            }
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
