package com.evefarm.service;

import com.evefarm.db.dao.DailyVolumeDao;
import com.evefarm.esi.EsiException;
import com.evefarm.esi.MarketsApi;
import com.evefarm.esi.dto.MarketHistoryDto;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class MarketHistoryService {

    public static final long THE_FORGE = 10_000_002L;
    static final int DAYS = 30;
    private static final Logger LOG = Logger.getLogger(MarketHistoryService.class.getName());
    private static final int PARALLEL_REQUESTS = 8;
    private static final LocalTime DAILY_REFRESH = LocalTime.of(11, 5);

    private final MarketsApi marketsApi;
    private final DailyVolumeDao dailyVolumeDao;
    private final Clock clock;

    public MarketHistoryService(MarketsApi marketsApi, DailyVolumeDao dailyVolumeDao) {
        this(marketsApi, dailyVolumeDao, Clock.systemUTC());
    }

    MarketHistoryService(MarketsApi marketsApi, DailyVolumeDao dailyVolumeDao, Clock clock) {
        this.marketsApi = marketsApi;
        this.dailyVolumeDao = dailyVolumeDao;
        this.clock = clock;
    }

    public Map<Integer, Double> dailyVolumes(Collection<Integer> typeIds) {
        Instant now = clock.instant();
        Instant refreshed = lastRefresh(now);
        Map<Integer, Double> volumes = new HashMap<>(dailyVolumeDao.findFetchedSince(THE_FORGE, typeIds, refreshed));
        List<Integer> missing = typeIds.stream().distinct().filter(typeId -> !volumes.containsKey(typeId)).toList();
        if (missing.isEmpty()) {
            return volumes;
        }
        LocalDate lastDay = refreshed.atZone(ZoneOffset.UTC).toLocalDate().minusDays(1);
        Map<Integer, Double> fetched = new ConcurrentHashMap<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_REQUESTS)) {
            for (int typeId : missing) {
                executor.submit(() -> fetch(typeId, lastDay).ifPresent(volume -> fetched.put(typeId, volume)));
            }
        }
        dailyVolumeDao.save(THE_FORGE, fetched, now);
        volumes.putAll(fetched);
        return volumes;
    }

    private Optional<Double> fetch(int typeId, LocalDate lastDay) {
        try {
            return Optional.of(dailyVolume(marketsApi.listRegionHistory(THE_FORGE, typeId), lastDay));
        } catch (EsiException e) {
            if (e.statusCode() == 400 || e.statusCode() == 404 || e.statusCode() == 422) {
                return Optional.of(0.0);
            }
            failed(typeId, e);
            return Optional.empty();
        } catch (RuntimeException e) {
            failed(typeId, e);
            return Optional.empty();
        }
    }

    static double dailyVolume(List<MarketHistoryDto> history, LocalDate lastDay) {
        LocalDate before = lastDay.minusDays(DAYS);
        long total = 0;
        for (MarketHistoryDto day : history) {
            LocalDate date = LocalDate.parse(day.date());
            if (date.isAfter(before) && !date.isAfter(lastDay)) {
                total += day.volume();
            }
        }
        return total / (double) DAYS;
    }

    static Instant lastRefresh(Instant now) {
        ZonedDateTime today = now.atZone(ZoneOffset.UTC).toLocalDate().atTime(DAILY_REFRESH).atZone(ZoneOffset.UTC);
        return (today.toInstant().isAfter(now) ? today.minusDays(1) : today).toInstant();
    }

    private static void failed(int typeId, RuntimeException e) {
        LOG.warning("Couldn't read the market history of type " + typeId + ": " + e.getMessage());
    }
}
