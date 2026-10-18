package com.evefarm.service;

import com.evefarm.db.Database;
import com.evefarm.db.MigrationRunner;
import com.evefarm.db.dao.CharacterDao;
import com.evefarm.db.dao.EncounterDao;
import com.evefarm.db.dao.KillDao;
import com.evefarm.db.dao.SettingsDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KillServiceTest {

    private static final long PILOT = 2124165849L;
    private static final String KILL_LOG = String.join("\n",
            "[ 2026.10.02 21:00:00 ] (combat) <color=0xff00ffff><b>100</b> <color=0x77ffffff><font size=10>to</font> "
                    + "<b><color=0xffffffff>Pithi Despoiler</b><font size=10><color=0x77ffffff> - Hits",
            "[ 2026.10.02 21:00:05 ] (bounty) <font size=12><b><color=0xff00aa00>30,000 ISK</b>"
                    + "<color=0x77ffffff> added to next bounty payout");

    @TempDir
    Path gamelogs;

    @Test
    void aGamelogThatCantBeReadDoesntStopTheOthers() throws IOException {
        Database database = new Database(":memory:");
        MigrationRunner.run(database);
        SettingsDao settings = new SettingsDao(database);
        settings.set(SettingsDao.GAMELOG_DIRECTORY, gamelogs.toString());
        CharacterDao characters = new CharacterDao(database);
        characters.upsert(PILOT, "Malpais Legate", List.of(), "owner");
        String broken = "20261001_120000_" + PILOT + ".txt";
        String fine = "20261002_120000_" + PILOT + ".txt";
        Files.writeString(gamelogs.resolve(broken), KILL_LOG);
        Files.writeString(gamelogs.resolve(fine), KILL_LOG);
        KillDao kills = mock(KillDao.class);
        when(kills.findProgress(broken)).thenThrow(new IllegalStateException("database is busy"));
        when(kills.findProgress(fine)).thenReturn(Optional.empty());
        NpcCatalogService npcs = mock(NpcCatalogService.class);
        when(npcs.catalog()).thenReturn(NpcCatalog.of(List.of()));
        KillService service = new KillService(settings, characters, kills, mock(EncounterDao.class), npcs);

        assertTrue(service.refreshKillsFromLogs());

        verify(kills).saveProgress(fine, Files.size(gamelogs.resolve(fine)), GameLogKillParser.PARSER_VERSION);
    }
}
