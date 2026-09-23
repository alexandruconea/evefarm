package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.db.dao.AssetDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.db.dao.SkillPointFilterDao;
import com.evefarm.db.dao.SnapshotDao;
import com.evefarm.esi.ClonesApi;
import com.evefarm.esi.ContractsApi;
import com.evefarm.esi.IndustryApi;
import com.evefarm.esi.LoyaltyApi;
import com.evefarm.esi.MarketsApi;
import com.evefarm.esi.SkillsApi;
import com.evefarm.esi.WalletApi;
import com.evefarm.esi.dto.ContractDto;
import com.evefarm.esi.dto.IndustryJobDto;
import com.evefarm.esi.dto.LoyaltyPointDto;
import com.evefarm.esi.dto.MarketOrderDto;
import com.evefarm.model.SkillPointFilter;
import com.evefarm.model.TrackerSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class TrackerSnapshotService {

    private static final int ACTIVITY_MANUFACTURING = 1;
    private static final int ACTIVITY_REACTIONS = 9;

    private static final int TYPE_ID_SKILL_EXTRACTOR = 40519;
    private static final int TYPE_ID_LARGE_SKILL_INJECTOR = 40520;
    private static final long MINIMUM_SKILL_POINTS = 5_000_000L;
    private static final long SKILL_EXTRACTOR_SIZE_SP = 500_000L;

    private final AuthService authService;
    private final WalletApi walletApi;
    private final ClonesApi clonesApi;
    private final MarketsApi marketsApi;
    private final ContractsApi contractsApi;
    private final IndustryApi industryApi;
    private final SkillsApi skillsApi;
    private final LoyaltyApi loyaltyApi;
    private final PriceService priceService;
    private final LpOfferPricingService lpOfferPricingService;
    private final AssetDao assetDao;
    private final SnapshotDao snapshotDao;
    private final SkillPointFilterDao skillPointFilterDao;
    private final SettingsDao settingsDao;

    public TrackerSnapshotService(AuthService authService, WalletApi walletApi, ClonesApi clonesApi,
                                   MarketsApi marketsApi, ContractsApi contractsApi, IndustryApi industryApi,
                                   SkillsApi skillsApi, LoyaltyApi loyaltyApi, PriceService priceService,
                                   LpOfferPricingService lpOfferPricingService, AssetDao assetDao,
                                   SnapshotDao snapshotDao, SkillPointFilterDao skillPointFilterDao,
                                   SettingsDao settingsDao) {
        this.authService = authService;
        this.walletApi = walletApi;
        this.clonesApi = clonesApi;
        this.marketsApi = marketsApi;
        this.contractsApi = contractsApi;
        this.industryApi = industryApi;
        this.skillsApi = skillsApi;
        this.loyaltyApi = loyaltyApi;
        this.priceService = priceService;
        this.lpOfferPricingService = lpOfferPricingService;
        this.assetDao = assetDao;
        this.snapshotDao = snapshotDao;
        this.skillPointFilterDao = skillPointFilterDao;
        this.settingsDao = settingsDao;
    }

    public TrackerSnapshot captureSnapshot(long characterId) {
        String accessToken = authService.getValidAccessToken(characterId);

        double walletBalance = walletApi.getBalance(characterId, accessToken);
        double assetsValue = assetDao.sumTotalValue(characterId);
        double implantsValue = implantsValue(characterId, accessToken);

        List<MarketOrderDto> orders = marketsApi.listCharacterOrders(characterId, accessToken);
        double sellOrdersValue = orders.stream()
                .filter(o -> !o.isBuyOrder())
                .mapToDouble(o -> o.price() * o.volumeRemain())
                .sum();
        double escrowValue = orders.stream()
                .filter(MarketOrderDto::isBuyOrder)
                .mapToDouble(o -> o.escrow() == null ? 0 : o.escrow())
                .sum();
        double escrowToCoverValue = orders.stream()
                .filter(MarketOrderDto::isBuyOrder)
                .mapToDouble(o -> Math.max(0, o.price() * o.volumeRemain() - (o.escrow() == null ? 0 : o.escrow())))
                .sum();

        List<ContractDto> contracts = contractsApi.listContracts(characterId, accessToken);
        double contractCollateralValue = contracts.stream()
                .filter(c -> "outstanding".equalsIgnoreCase(c.status()))
                .mapToDouble(c -> c.collateral() == null ? 0 : c.collateral())
                .sum();
        double contractsValue = contracts.stream()
                .filter(c -> "outstanding".equalsIgnoreCase(c.status()))
                .mapToDouble(c -> (c.price() == null ? 0 : c.price()) + (c.reward() == null ? 0 : c.reward()))
                .sum();

        List<IndustryJobDto> jobs = industryApi.listActiveJobs(characterId, accessToken);
        double manufacturingValue = jobs.stream()
                .filter(j -> (j.activityId() == ACTIVITY_MANUFACTURING || j.activityId() == ACTIVITY_REACTIONS)
                        && j.productTypeId() != null)
                .mapToDouble(j -> (j.runs() == null ? 1 : j.runs()) * priceService.getUnitPrice(j.productTypeId()).orElse(0))
                .sum();

        long skillPoints = skillsApi.getSkills(characterId, accessToken).totalSp();
        SkillPointFilter filter = skillPointFilterDao.find(characterId);
        double skillPointValue = filter.enabled() ? skillPointValue(skillPoints, filter.minimumSp()) : 0;

        double lpValue = lpValue(characterId, accessToken);

        TrackerSnapshot snapshot = TrackerSnapshot.of(characterId, Instant.now(), walletBalance, assetsValue,
                implantsValue, sellOrdersValue, escrowValue, escrowToCoverValue, manufacturingValue,
                contractCollateralValue, contractsValue, skillPoints, skillPointValue, lpValue);
        snapshotDao.insert(snapshot);
        return snapshot;
    }

    private double lpValue(long characterId, String accessToken) {
        String favoriteCorpId = settingsDao.getOrDefault(SettingsDao.LP_STORE_FAVORITE_CORPORATION_ID, "");
        if (favoriteCorpId.isBlank()) {
            return 0;
        }
        long corporationId;
        try {
            corporationId = Long.parseLong(favoriteCorpId);
        } catch (NumberFormatException e) {
            return 0;
        }

        long lpBalance = loyaltyApi.listLoyaltyPoints(characterId, accessToken).stream()
                .filter(p -> p.corporationId() == corporationId)
                .mapToLong(LoyaltyPointDto::loyaltyPoints)
                .findFirst().orElse(0);
        if (lpBalance <= 0) {
            return 0;
        }

        double bestIskPerLp = lpOfferPricingService.listPricedOffers(corporationId).stream()
                .flatMap(offer -> java.util.stream.Stream.of(
                        liquidIskPerLp(offer.iskPerLpSell(), offer.fivePercentSellVolume(), offer.quantity()),
                        liquidIskPerLp(offer.iskPerLpBuy(), offer.fivePercentBuyVolume(), offer.quantity())))
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max().orElse(0);

        return bestIskPerLp * lpBalance;
    }

    Double liquidIskPerLp(Double iskPerLp, Double fivePercentVolume, long offerQuantity) {
        if (iskPerLp == null || fivePercentVolume == null || fivePercentVolume < offerQuantity) {
            return null;
        }
        return iskPerLp;
    }

    private double skillPointValue(long totalSkillPoints, long extraMinimumSp) {
        if (totalSkillPoints < MINIMUM_SKILL_POINTS) {
            return 0;
        }
        double extractorPrice = priceService.getUnitPrice(TYPE_ID_SKILL_EXTRACTOR).orElse(0);
        double injectorPrice = priceService.getUnitPrice(TYPE_ID_LARGE_SKILL_INJECTOR).orElse(0);
        long floor = Math.max(MINIMUM_SKILL_POINTS, extraMinimumSp);
        long extractors = (totalSkillPoints - floor) / SKILL_EXTRACTOR_SIZE_SP;
        if (extractors < 1) {
            return 0;
        }
        return extractors * (injectorPrice - extractorPrice);
    }

    private double implantsValue(long characterId, String accessToken) {
        List<Integer> implantTypeIds = clonesApi.listImplants(characterId, accessToken);
        double total = 0;
        for (Integer typeId : implantTypeIds) {
            total += priceService.getUnitPrice(typeId).orElse(0);
        }
        return total;
    }

}
