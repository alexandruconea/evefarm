package com.evefarm.service;

import com.evefarm.model.EveCharacter;
import com.evefarm.model.LpNetValues;
import com.evefarm.model.LpOfferRow;
import com.evefarm.model.OwnedSkill;
import com.evefarm.model.SellingFees;
import com.evefarm.model.StandingRow;

import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

public final class SellingFeesService {

    static final long JITA_STATION_CORPORATION = 1000035L;
    static final long JITA_STATION_FACTION = 500001L;
    static final int ASSUMED_LEVEL = 4;

    private final CharacterService characterService;
    private final SkillService skillService;
    private final StandingService standingService;

    public SellingFeesService(CharacterService characterService, SkillService skillService,
                              StandingService standingService) {
        this.characterService = characterService;
        this.skillService = skillService;
        this.standingService = standingService;
    }

    public SellingFees mainCharacterFees() {
        List<EveCharacter> characters = characterService.listCharactersMainFirst();
        if (characters.isEmpty()) {
            return fees(null, skillId -> ASSUMED_LEVEL, Map.of());
        }
        EveCharacter seller = characters.getFirst();
        return characterFees(seller.characterId(), seller.characterName());
    }

    public SellingFees characterFees(long characterId, String characterName) {
        Map<Integer, Integer> levels = skillService.skills(characterId)
                .map(skills -> skills.skills().stream()
                        .collect(Collectors.toMap(OwnedSkill::skillId, OwnedSkill::activeLevel, (a, b) -> a)))
                .orElse(null);
        Map<Long, Double> standings = standingService.getRows().stream()
                .filter(row -> row.characterId() == characterId)
                .collect(Collectors.toMap(StandingRow::fromId, StandingRow::standing, (a, b) -> a));
        return fees(characterName, skillId -> levels == null ? ASSUMED_LEVEL : levels.getOrDefault(skillId, 0),
                standings);
    }

    static SellingFees fees(String characterName, IntUnaryOperator level, Map<Long, Double> standings) {
        return new SellingFees(characterName, IndustryCalculator.salesTax(level),
                IndustryCalculator.brokerFee(level, standings.getOrDefault(JITA_STATION_FACTION, 0.0),
                        standings.getOrDefault(JITA_STATION_CORPORATION, 0.0)));
    }

    public static LpOfferRow afterFees(LpOfferRow row, SellingFees fees) {
        Double profitSell = netProfit(row, row.sellPricePerUnit(), fees.salesTax(), fees.brokerFee());
        Double profitBuy = netProfit(row, row.buyPricePerUnit(), fees.salesTax(), 0);
        return row.withNet(new LpNetValues(perLp(row, profitSell), perLp(row, profitBuy), profitSell, profitBuy));
    }

    private static Double netProfit(LpOfferRow row, Double unitPrice, double salesTax, double brokerFee) {
        Double otherCost = row.otherCost();
        if (unitPrice == null || otherCost == null) {
            return null;
        }
        return IndustryCalculator.sell(row.outputUnits(), unitPrice, salesTax, brokerFee).net() - row.iskCost()
                - otherCost;
    }

    private static Double perLp(LpOfferRow row, Double profit) {
        return profit == null || row.lpCost() <= 0 ? null : profit / row.lpCost();
    }
}
