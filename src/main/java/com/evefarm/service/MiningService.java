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

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        return miningLedgerDao.listRows(period.fromDate(LocalDate.now(ZoneOffset.UTC))).stream()
                .map(row -> toRow(row, unitValue(valuation, row.typeId(), ores.get(row.typeId()), prices, refineRate)))
                .toList();
    }

    private static MiningRow toRow(MiningLedgerRow row, Double unitValue) {
        return new MiningRow(row.characterId(), row.characterName(), row.date(), row.systemName(), row.typeId(),
                row.oreName(), kind(row.groupName()), row.quantity(), row.quantity() * row.unitVolume(), unitValue);
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
        Double raw = prices.get(typeId);
        if (ore == null) {
            return raw;
        }
        if (valuation == Valuation.COMPRESSED && ore.compressedTypeId() != null) {
            Double compressed = prices.get(ore.compressedTypeId());
            return compressed == null ? raw : compressed;
        }
        if (valuation == Valuation.REFINED && !ore.materials().isEmpty()) {
            double total = 0;
            boolean priced = false;
            for (Map.Entry<Integer, Long> material : ore.materials().entrySet()) {
                Double price = prices.get(material.getKey());
                if (price != null) {
                    total += price * material.getValue();
                    priced = true;
                }
            }
            return priced ? total * refineRate / ore.portionSize() : raw;
        }
        return raw;
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
