package com.evefarm.service;

import com.evefarm.db.dao.AbyssalRunDao;
import com.evefarm.db.dao.ItemTypeDao;
import com.evefarm.model.AbyssTier;
import com.evefarm.model.AbyssWeather;
import com.evefarm.model.AbyssalLoot;
import com.evefarm.model.ItemType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

public final class AbyssLootService {

    public record LootResult(List<AbyssalLoot> items, List<String> unknownNames, List<AbyssalLoot> ignored) {
        public double totalValue() {
            return items.stream().mapToDouble(AbyssalLoot::totalValue).sum();
        }
    }

    private final ItemTypeDao itemTypeDao;
    private final PriceService priceService;
    private final AbyssalRunDao runDao;

    public AbyssLootService(ItemTypeDao itemTypeDao, PriceService priceService, AbyssalRunDao runDao) {
        this.itemTypeDao = itemTypeDao;
        this.priceService = priceService;
        this.runDao = runDao;
    }

    public LootResult calculate(String cargoBefore, String cargoAfter) {
        Map<String, Long> gained = CargoParser.gained(CargoParser.parse(cargoBefore), CargoParser.parse(cargoAfter));
        Map<String, ItemType> types = itemTypeDao.findByNames(gained.keySet());
        priceService.ensureFreshPrices(types.values().stream().map(ItemType::typeId).toList());
        Set<Integer> ignoredIds = runDao.listIgnoredItems().keySet();
        List<AbyssalLoot> items = new ArrayList<>();
        List<AbyssalLoot> ignored = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        gained.forEach((name, quantity) -> {
            ItemType type = types.get(name.toLowerCase(Locale.ROOT));
            if (type == null) {
                unknown.add(name);
                return;
            }
            AbyssalLoot item = new AbyssalLoot(type.typeId(), type.typeName(), quantity, price(type.typeId()));
            (ignoredIds.contains(type.typeId()) ? ignored : items).add(item);
        });
        items.sort(Comparator.comparingDouble(AbyssalLoot::totalValue).reversed()
                .thenComparing(AbyssalLoot::typeName, String.CASE_INSENSITIVE_ORDER));
        return new LootResult(List.copyOf(items), List.copyOf(unknown), List.copyOf(ignored));
    }

    public int recognizedItems(String cargo) {
        Map<String, Long> items = CargoParser.parse(cargo);
        if (items.isEmpty() || itemTypeDao.findByNames(items.keySet()).isEmpty()) {
            return 0;
        }
        return items.size();
    }

    public Double filamentCost(AbyssTier tier, AbyssWeather weather) {
        if (tier == null || weather == null) {
            return null;
        }
        String name = AbyssWeather.filamentName(tier, weather);
        ItemType filament = itemTypeDao.findByNames(List.of(name)).get(name.toLowerCase(Locale.ROOT));
        if (filament == null) {
            return null;
        }
        priceService.ensureFreshPrices(List.of(filament.typeId()));
        return price(filament.typeId());
    }

    private Double price(int typeId) {
        OptionalDouble price = priceService.getUnitPrice(typeId);
        return price.isPresent() ? price.getAsDouble() : null;
    }
}
