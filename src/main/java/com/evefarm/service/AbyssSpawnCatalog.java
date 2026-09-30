package com.evefarm.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class AbyssSpawnCatalog {

    public enum Faction {
        TRIGLAVIANS("Triglavians"),
        ROGUE_DRONES("Rogue drones"),
        DRIFTERS("Drifters"),
        SLEEPERS("Sleepers"),
        ANGELS("Angels"),
        SANSHA("Sansha"),
        EDENCOM("EDENCOM");

        private final String label;

        Faction(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Ewar {
        NEUT("neuts"),
        SCRAM("scrams"),
        WEB("webs"),
        PAINTER("target painters"),
        DAMP("sensor damps"),
        TRACKING("tracking disruptors"),
        REPAIR("remote repairs");

        private final String label;

        Ewar(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Npc(String name, Faction faction, Set<Ewar> ewar, String heavy) {
    }

    public record RoomReport(int room, boolean followUp, List<Faction> factions, List<String> npcs,
                             List<String> heavies, List<String> advice, List<Ewar> ewar) {

        public boolean hasNews() {
            return !factions.isEmpty() || !heavies.isEmpty() || !advice.isEmpty() || !ewar.isEmpty();
        }

        public RoomReport since(RoomReport spoken) {
            return new RoomReport(room, true,
                    factions.stream().filter(faction -> !spoken.factions().contains(faction)).toList(), npcs,
                    heavies.stream().filter(heavy -> !spoken.heavies().contains(heavy)).toList(),
                    advice.stream().filter(tip -> !spoken.advice().contains(tip)).toList(),
                    ewar.stream().filter(kind -> !spoken.ewar().contains(kind)).toList());
        }

        public String speech() {
            StringBuilder text = new StringBuilder(followUp ? "Also in room " : "Room ").append(room).append(':');
            List<String> heavyNames = heavies.stream().limit(2).toList();
            if (!factions.isEmpty()) {
                text.append(' ').append(joined(factions.stream().map(Faction::label).toList()));
                if (!heavyNames.isEmpty()) {
                    text.append(", with ").append(joined(heavyNames));
                }
                text.append('.');
            } else if (!heavyNames.isEmpty()) {
                text.append(' ').append(joined(heavyNames)).append('.');
            }
            for (String tip : advice) {
                text.append(' ').append(tip);
            }
            if (!ewar.isEmpty()) {
                String kinds = joined(ewar.stream().limit(3).map(Ewar::label).toList());
                text.append(text.charAt(text.length() - 1) == ':' ? " watch for " : " Watch for ").append(kinds).append('.');
            }
            return text.toString();
        }
    }

    private static final Map<String, Faction> SHIP_FACTIONS = Map.ofEntries(
            Map.entry("Damavik", Faction.TRIGLAVIANS), Map.entry("Kikimora", Faction.TRIGLAVIANS),
            Map.entry("Vedmak", Faction.TRIGLAVIANS), Map.entry("Rodiva", Faction.TRIGLAVIANS),
            Map.entry("Drekavac", Faction.TRIGLAVIANS), Map.entry("Leshak", Faction.TRIGLAVIANS),
            Map.entry("Swarmer", Faction.TRIGLAVIANS),
            Map.entry("Tessella", Faction.ROGUE_DRONES), Map.entry("Tessera", Faction.ROGUE_DRONES),
            Map.entry("Overmind", Faction.ROGUE_DRONES),
            Map.entry("Tyrannos", Faction.DRIFTERS),
            Map.entry("Troop", Faction.EDENCOM));

    private static final Map<String, Faction> FIRST_WORD_FACTIONS = Map.of(
            "Ephialtes", Faction.DRIFTERS,
            "Drifter", Faction.DRIFTERS,
            "Tyrannos", Faction.DRIFTERS,
            "Lucid", Faction.SLEEPERS,
            "Awoken", Faction.SLEEPERS,
            "Devoted", Faction.SANSHA);

    private static final Map<Ewar, List<String>> EWAR_WORDS = Map.of(
            Ewar.NEUT, List.of("Starving", "Nullcharge", "Dissipator", "Firewatcher", "Sentinel", "Smith", "Fury",
                    "Cynabal", "Drainer"),
            Ewar.SCRAM, List.of("Anchoring", "Nullwarp", "Spearfisher", "Trapper", "Echo"),
            Ewar.WEB, List.of("Tangling", "Snarecaster", "Entanglement", "Entangler", "Warden", "Upholder", "Fisher",
                    "Knight", "Echo", "Cynabal", "Arrester", "Benthic", "Endobenthic"),
            Ewar.PAINTER, List.of("Shining", "Spotlighter", "Illuminator", "Deepwatcher", "Torchbearer", "Swordspine",
                    "Ixion", "Marker"),
            Ewar.DAMP, List.of("Blinding", "Obfuscator", "Lookout", "Medusa", "Gazedimmer"),
            Ewar.TRACKING, List.of("Ghosting", "Fogcaster", "Confuser", "Herald", "Ixion"),
            Ewar.REPAIR, List.of("Renewing", "Plateforger", "Fieldweaver", "Preserver", "Priest", "Burst"));

    private static final List<String> HEAVIES = List.of("Karybdis", "Leshak", "Drekavac", "Overmind", "Tessera",
            "Marshal", "Vedmak", "Kikimora", "Aegis", "Watchman", "Lancer", "Cynabal", "Knight");

    private AbyssSpawnCatalog() {
    }

    public static Optional<Npc> identify(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String[] words = name.strip().split("\\s+");
        Faction faction = SHIP_FACTIONS.get(words[words.length - 1]);
        if (faction == null) {
            faction = FIRST_WORD_FACTIONS.get(words[0]);
        }
        if (faction == null && List.of(words).contains("Lucifer")) {
            faction = Faction.ANGELS;
        }
        if (faction == null) {
            return Optional.empty();
        }
        Set<String> wordSet = Set.of(words);
        EnumSet<Ewar> ewar = EnumSet.noneOf(Ewar.class);
        for (Map.Entry<Ewar, List<String>> entry : EWAR_WORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(wordSet::contains)) {
                ewar.add(entry.getKey());
            }
        }
        if (name.startsWith("Elite Lucifer Dramiel")) {
            ewar.add(Ewar.WEB);
        }
        String heavy = HEAVIES.stream().filter(wordSet::contains).findFirst().orElse(null);
        return Optional.of(new Npc(name.strip(), faction, ewar, heavy));
    }

    public static RoomReport report(int room, Collection<String> names) {
        List<Npc> npcs = names.stream().map(AbyssSpawnCatalog::identify).flatMap(Optional::stream).toList();
        Set<Faction> factions = new LinkedHashSet<>();
        Set<String> heavies = new LinkedHashSet<>();
        EnumSet<Ewar> ewar = EnumSet.noneOf(Ewar.class);
        for (Npc npc : npcs) {
            factions.add(npc.faction());
            ewar.addAll(npc.ewar());
            if (npc.heavy() != null) {
                heavies.add(npc.heavy());
            }
        }
        List<String> orderedHeavies = HEAVIES.stream().filter(heavies::contains).toList();
        return new RoomReport(room, false, List.copyOf(factions), npcs.stream().map(Npc::name).toList(),
                orderedHeavies, advice(npcs, factions), List.copyOf(ewar));
    }

    private static List<String> advice(List<Npc> npcs, Set<Faction> factions) {
        List<String> advice = new ArrayList<>();
        if (npcs.stream().anyMatch(npc -> npc.name().startsWith("Karybdis"))) {
            advice.add("Karybdis: spiral in to under 30 kilometers.");
        }
        if (npcs.stream().anyMatch(npc -> npc.name().endsWith("Tessera"))) {
            advice.add("Tessera hit hard up close: stay over 12 kilometers away.");
        }
        if (npcs.stream().anyMatch(npc -> (" " + npc.name()).contains(" Vila "))) {
            advice.add("Kill the Vila ships to stop their drones; killed drones come back.");
        }
        if (factions.contains(Faction.EDENCOM)) {
            advice.add("Keep your speed up until the room is under control.");
            if (npcs.stream().anyMatch(npc -> npc.name().contains("Marshal"))) {
                advice.add("Kill the Marshal first, it has little health.");
            }
        }
        if (factions.contains(Faction.SANSHA)) {
            advice.add("Kite the Sansha: they are slow but very dangerous up close.");
        }
        return advice;
    }

    private static String joined(List<String> parts) {
        if (parts.size() <= 1) {
            return parts.isEmpty() ? "" : parts.getFirst();
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
    }
}
