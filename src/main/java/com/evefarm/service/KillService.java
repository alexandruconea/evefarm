package com.evefarm.service;

import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.EncounterDao;
import com.evefarm.db.dao.KillDao;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.KillDayTypeRow;
import com.evefarm.model.ParsedKill;
import com.evefarm.util.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class KillService {

    private static final Logger LOG = Logger.getLogger(KillService.class.getName());

    private final SettingsDao settingsDao;
    private final CharacterDao characterDao;
    private final KillDao killDao;
    private final EncounterDao encounterDao;
    private final NpcCatalogService npcCatalogService;
    private final List<Runnable> logChangeListeners = new CopyOnWriteArrayList<>();

    public KillService(SettingsDao settingsDao, CharacterDao characterDao, KillDao killDao,
                       EncounterDao encounterDao, NpcCatalogService npcCatalogService) {
        this.settingsDao = settingsDao;
        this.characterDao = characterDao;
        this.killDao = killDao;
        this.encounterDao = encounterDao;
        this.npcCatalogService = npcCatalogService;
    }

    public Path gameLogDirectory() {
        String configured = settingsDao.get(SettingsDao.GAMELOG_DIRECTORY).orElse(null);
        return (configured == null || configured.isBlank())
                ? AppPaths.defaultGameLogDirectory() : Paths.get(configured);
    }

    public void addLogChangeListener(Runnable listener) {
        logChangeListeners.add(listener);
    }

    public boolean hasGameLogDirectory() {
        return Files.isDirectory(gameLogDirectory());
    }

    public synchronized boolean refreshKillsFromLogs() {
        Path directory = gameLogDirectory();
        if (!Files.isDirectory(directory)) {
            LOG.warning("Gamelog directory not found: " + directory
                    + " - check the path in Settings if this looks wrong");
            return false;
        }

        Set<Long> knownCharacterIds = new HashSet<>();
        for (EveCharacter character : characterDao.listAll()) {
            knownCharacterIds.add(character.characterId());
        }
        if (knownCharacterIds.isEmpty()) {
            return false;
        }

        npcCatalogService.refreshIfStale();
        NpcCatalog catalog = npcCatalogService.catalog();

        boolean changed = false;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.txt")) {
            for (Path file : files) {
                changed |= scanFile(file, knownCharacterIds, catalog);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to list Gamelog directory " + directory, e);
        }
        if (changed) {
            for (Runnable listener : logChangeListeners) {
                listener.run();
            }
        }
        return changed;
    }

    private boolean scanFile(Path file, Set<Long> knownCharacterIds, NpcCatalog catalog) {
        String fileName = file.getFileName().toString();
        Optional<Long> characterId = GameLogKillParser.extractCharacterId(fileName);
        if (characterId.isEmpty() || !knownCharacterIds.contains(characterId.get())) {
            return false;
        }

        try {
            long currentSize = Files.size(file);
            Optional<KillDao.LogFileProgress> progress = killDao.findProgress(fileName);
            if (progress.isPresent() && progress.get().size() == currentSize
                    && progress.get().parserVersion() >= GameLogKillParser.PARSER_VERSION) {
                return false;
            }

            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            GameLogKillParser.ParseResult parsed = GameLogKillParser.parse(lines);
            for (ParsedKill kill : parsed.kills()) {
                String label = catalog.factionLabelFor(kill.npcName()).orElse(kill.factionLabel());
                killDao.insertIfNew(characterId.get(), kill.withFactionLabel(label));
            }
            encounterDao.replaceForLogFile(characterId.get(), fileName, parsed.encounters());
            killDao.saveProgress(fileName, currentSize, GameLogKillParser.PARSER_VERSION);
            return true;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to read Gamelog file " + file, e);
            return false;
        }
    }

    public List<String> listFactionOptions(Set<Long> characterIds) {
        return killDao.listDistinctFactions(characterIds);
    }

    public List<String> listSystemOptions(Set<Long> characterIds, Set<String> factionLabels) {
        return killDao.listDistinctSystems(characterIds, factionLabels);
    }

    public List<String> listShipTypeOptions(Set<Long> characterIds, Set<String> factionLabels, String systemFilter) {
        return killDao.listDistinctNpcNames(characterIds, factionLabels, systemFilter);
    }

    public List<KillDayTypeRow> listDayTypeRows(Set<Long> characterIds, Set<String> factionLabels,
                                                 Set<String> npcNames, String systemFilter,
                                                 Instant from, Instant to) {
        return killDao.listDayTypeRows(characterIds, factionLabels, npcNames, systemFilter, from, to);
    }
}
