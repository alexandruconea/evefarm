package com.evefarm.service;

import com.evefarm.auth.AuthService;
import com.evefarm.auth.OAuthConfig;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.StandingDao;
import com.evefarm.esi.StandingsApi;
import com.evefarm.esi.dto.StandingDto;
import com.evefarm.model.StandingEntry;
import com.evefarm.model.StandingRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class StandingService {

    public static final String FACTION = "faction";
    public static final String CORPORATION = "npc_corp";
    public static final String AGENT = "agent";

    private static final Comparator<StandingRow> BEST_FIRST =
            Comparator.comparingDouble(StandingRow::standing).reversed().thenComparing(StandingRow::name);

    private final AuthService authService;
    private final CharacterDao characterDao;
    private final StandingsApi standingsApi;
    private final EntityNameCacheService entityNameCacheService;
    private final StandingDao standingDao;

    public StandingService(AuthService authService, CharacterDao characterDao, StandingsApi standingsApi,
                           EntityNameCacheService entityNameCacheService, StandingDao standingDao) {
        this.authService = authService;
        this.characterDao = characterDao;
        this.standingsApi = standingsApi;
        this.entityNameCacheService = entityNameCacheService;
        this.standingDao = standingDao;
    }

    public void refreshStandingsForCharacter(long characterId) {
        CharacterScopes.require(characterDao, characterId, OAuthConfig.STANDINGS_SCOPE, "standings");
        String accessToken = authService.getValidAccessToken(characterId);
        List<StandingDto> standings = standingsApi.listStandings(characterId, accessToken);
        entityNameCacheService.resolveEntities(standings.stream().map(StandingDto::fromId).toList());
        standingDao.replaceForCharacter(characterId, standings.stream()
                .map(standing -> new StandingEntry(standing.fromId(), standing.fromType(), standing.standing()))
                .toList());
    }

    public List<StandingRow> getRows() {
        return standingDao.listRows();
    }

    public static List<StandingRow> factionsAndCorporations(List<StandingRow> rows) {
        Map<Long, List<StandingRow>> byCharacter = new LinkedHashMap<>();
        for (StandingRow row : rows) {
            if (!AGENT.equals(row.fromType())) {
                byCharacter.computeIfAbsent(row.characterId(), id -> new ArrayList<>()).add(row);
            }
        }
        List<StandingRow> result = new ArrayList<>();
        for (List<StandingRow> characterRows : byCharacter.values()) {
            Map<String, List<StandingRow>> corporationsByFaction = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            List<StandingRow> withoutFaction = new ArrayList<>();
            for (StandingRow row : characterRows) {
                if (CORPORATION.equals(row.fromType())) {
                    if (row.factionName() == null || row.factionName().isBlank()) {
                        withoutFaction.add(row);
                    } else {
                        corporationsByFaction.computeIfAbsent(row.factionName(), name -> new ArrayList<>()).add(row);
                    }
                }
            }
            characterRows.stream()
                    .filter(row -> FACTION.equals(row.fromType()))
                    .sorted(BEST_FIRST)
                    .forEach(faction -> {
                        result.add(faction);
                        addBestFirst(result, corporationsByFaction.remove(faction.name()));
                    });
            corporationsByFaction.values().forEach(corporations -> addBestFirst(result, corporations));
            addBestFirst(result, withoutFaction);
        }
        return result;
    }

    public static Set<StandingRow> corporationsUnderTheirFaction(List<StandingRow> rows) {
        Set<String> factions = new HashSet<>();
        for (StandingRow row : rows) {
            if (FACTION.equals(row.fromType())) {
                factions.add(row.characterId() + "|" + row.name());
            }
        }
        Set<StandingRow> result = new HashSet<>();
        for (StandingRow row : rows) {
            if (CORPORATION.equals(row.fromType()) && factions.contains(row.characterId() + "|" + row.factionName())) {
                result.add(row);
            }
        }
        return result;
    }

    public static List<StandingRow> agents(List<StandingRow> rows, StandingRow selected) {
        return rows.stream()
                .filter(row -> AGENT.equals(row.fromType()))
                .filter(row -> selected == null || belongsTo(row, selected))
                .toList();
    }

    static boolean belongsTo(StandingRow agent, StandingRow selected) {
        if (agent.characterId() != selected.characterId()) {
            return false;
        }
        if (FACTION.equals(selected.fromType())) {
            return selected.name().equals(agent.factionName());
        }
        return CORPORATION.equals(selected.fromType()) && selected.name().equals(agent.corporationName());
    }

    private static void addBestFirst(List<StandingRow> result, List<StandingRow> rows) {
        if (rows != null) {
            rows.stream().sorted(BEST_FIRST).forEach(result::add);
        }
    }

    public static String kind(String fromType) {
        if (fromType == null) {
            return "";
        }
        return switch (fromType) {
            case FACTION -> "Faction";
            case CORPORATION -> "Corporation";
            case AGENT -> "Agent";
            default -> fromType;
        };
    }
}
