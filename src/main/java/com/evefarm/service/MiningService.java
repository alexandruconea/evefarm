package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.auth.OAuthConfig;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.MiningLedgerDao;
import com.evefarm.esi.IndustryApi;
import com.evefarm.esi.dto.MiningLedgerDto;
import com.evefarm.model.MiningEntry;
import com.evefarm.model.MiningLedgerRow;
import com.evefarm.model.MiningRow;
import com.evefarm.model.Ore;
import com.evefarm.model.RefinedMaterial;
import com.evefarm.model.TypeInfo;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class MiningService {

    public enum Valuation {
        ORE("Ore price"),
        COMPRESSED("Compressed ore price"),
        REFINED("Refined minerals");

        private final String label;

        Valuation(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Period {
        TODAY("Today"),
        WEEK("Last 7 days"),
        MONTH("Last 30 days"),
        THIS_MONTH("This month"),
        ALL("All time");

        private final String label;

        Period(String label) {
            this.label = label;
        }

        public String fromDate(LocalDate today) {
            return switch (this) {
                case TODAY -> today.toString();
                case WEEK -> today.minusDays(6).toString();
                case MONTH -> today.minusDays(29).toString();
                case THIS_MONTH -> today.withDayOfMonth(1).toString();
                case ALL -> null;
            };
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final AuthService authService;
    private final CharacterDao characterDao;
    private final IndustryApi industryApi;
    private final TypeNameCacheService typeNameCacheService;
    private final LocationNameCacheService locationNameCacheService;
    private final MiningLedgerDao miningLedgerDao;
    private final OreCatalogService oreCatalogService;
    private final PriceService priceService;

    public MiningService(AuthService authService, CharacterDao characterDao, IndustryApi industryApi,
                         TypeNameCacheService typeNameCacheService, LocationNameCacheService locationNameCacheService,
                         MiningLedgerDao miningLedgerDao, OreCatalogService oreCatalogService,
                         PriceService priceService) {
        this.authService = authService;
        this.characterDao = characterDao;
        this.industryApi = industryApi;
        this.typeNameCacheService = typeNameCacheService;
        this.locationNameCacheService = locationNameCacheService;
        this.miningLedgerDao = miningLedgerDao;
        this.oreCatalogService = oreCatalogService;
        this.priceService = priceService;
    }

    public void refreshLedgerForCharacter(long characterId) {
        CharacterScopes.require(characterDao, characterId, OAuthConfig.MINING_SCOPE, "mining ledger");
        String accessToken = authService.getValidAccessToken(characterId);
        List<MiningLedgerDto> ledger = industryApi.listMiningLedger(characterId, accessToken);
        oreCatalogService.refreshIfStale();

        Set<Integer> typeIds = new HashSet<>();
        Set<Long> systemIds = new HashSet<>();
        for (MiningLedgerDto entry : ledger) {
            typeIds.add(entry.typeId());
            systemIds.add(entry.solarSystemId());
        }
        Set<Integer> pricedTypes = pricedTypes(typeIds, oreCatalogService.ores());
        typeNameCacheService.resolveTypes(pricedTypes);
        locationNameCacheService.resolveLocations(systemIds, accessToken);
        priceService.ensureFreshPrices(pricedTypes);

        miningLedgerDao.saveForCharacter(characterId, ledger.stream()
                .map(entry -> new MiningEntry(entry.date(), entry.solarSystemId(), entry.typeId(), entry.quantity()))
                .toList());
    }

    public List<MiningRow> getRows(Period period, Valuation valuation, double refineRate) {
        Map<Integer, Ore> ores = oreCatalogService.ores();
        Map<Integer, Double> prices = priceService.getUnitPrices();
        Map<Integer, TypeInfo> materialTypes = new HashMap<>();
        Function<Integer, TypeInfo> types = typeId ->
                materialTypes.computeIfAbsent(typeId, typeNameCacheService::resolveType);
        return miningLedgerDao.listRows(period.fromDate(LocalDate.now(ZoneOffset.UTC))).stream()
                .map(row -> toRow(row, valuation, ores.get(row.typeId()), prices, refineRate, types))
                .toList();
    }

    private static MiningRow toRow(MiningLedgerRow row, Valuation valuation, Ore ore, Map<Integer, Double> prices,
                                   double refineRate, Function<Integer, TypeInfo> types) {
        Integer compressed = compressedType(valuation, ore, prices);
        List<RefinedMaterial> materials = refines(valuation, ore, prices)
                ? refinedMaterials(row.quantity(), ore, prices, refineRate, types)
                : List.of();
        return new MiningRow(row.characterId(), row.characterName(), row.date(), row.systemName(), row.typeId(),
                row.oreName(), kind(row.groupName()), row.quantity(), row.quantity() * row.unitVolume(),
                unitValue(valuation, row.typeId(), ore, prices, refineRate),
                compressed == null ? row.typeId() : compressed, materials);
    }

    static Set<Integer> pricedTypes(Set<Integer> minedTypes, Map<Integer, Ore> ores) {
        Set<Integer> result = new HashSet<>(minedTypes);
        for (int typeId : minedTypes) {
            Ore ore = ores.get(typeId);
            if (ore != null) {
                if (ore.compressedTypeId() != null) {
                    result.add(ore.compressedTypeId());
                }
                result.addAll(ore.materials().keySet());
            }
        }
        return result;
    }

    static Double unitValue(Valuation valuation, int typeId, Ore ore, Map<Integer, Double> prices, double refineRate) {
        Integer compressed = compressedType(valuation, ore, prices);
        if (compressed != null) {
            return prices.get(compressed);
        }
        if (refines(valuation, ore, prices)) {
            double total = 0;
            for (Map.Entry<Integer, Long> material : ore.materials().entrySet()) {
                Double price = prices.get(material.getKey());
                if (price != null) {
                    total += price * material.getValue();
                }
            }
            return total * refineRate / ore.portionSize();
        }
        return prices.get(typeId);
    }

    // The compressed ore is used only when it has a price - otherwise the ore is valued as mined.
    static Integer compressedType(Valuation valuation, Ore ore, Map<Integer, Double> prices) {
        return valuation == Valuation.COMPRESSED && ore != null && ore.compressedTypeId() != null
                && prices.get(ore.compressedTypeId()) != null ? ore.compressedTypeId() : null;
    }

    // Likewise the minerals are used only when at least one of them has a price.
    static boolean refines(Valuation valuation, Ore ore, Map<Integer, Double> prices) {
        return valuation == Valuation.REFINED && ore != null
                && ore.materials().keySet().stream().anyMatch(materialId -> prices.get(materialId) != null);
    }

    static List<RefinedMaterial> refinedMaterials(long quantity, Ore ore, Map<Integer, Double> prices,
                                                  double refineRate, Function<Integer, TypeInfo> types) {
        double portions = (double) quantity / ore.portionSize();
        return ore.materials().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(material -> {
                    TypeInfo type = types.apply(material.getKey());
                    double amount = material.getValue() * portions * refineRate;
                    Double price = prices.get(material.getKey());
                    return new RefinedMaterial(material.getKey(), type.name(), amount, amount * type.volume(),
                            price == null ? null : price * amount);
                })
                .toList();
    }

    static String kind(String groupName) {
        if (groupName == null) {
            return "Ore";
        }
        String group = groupName.toLowerCase(Locale.ROOT);
        if (group.contains("moon")) {
            return "Moon ore";
        }
        if (group.equals("ice")) {
            return "Ice";
        }
        if (group.contains("cloud") || group.contains("gas")) {
            return "Gas";
        }
        return "Ore";
    }
}
