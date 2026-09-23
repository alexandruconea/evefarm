package com.evefarm.service;

import com.evefarm.model.NpcType;
import com.evefarm.model.SpawnClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class NpcCatalog {

    public static final String MISSIONS_LABEL = "Missions";

    public static final NpcCatalog EMPTY = new NpcCatalog(Map.of(), Map.of());

    public record Entry(String name, String groupName, SpawnClass spawnClass, List<Integer> typeIds) {
    }

    private final Map<String, Entry> byName;
    private final Map<Integer, String> nameByTypeId;

    private NpcCatalog(Map<String, Entry> byName, Map<Integer, String> nameByTypeId) {
        this.byName = byName;
        this.nameByTypeId = nameByTypeId;
    }

    public static NpcCatalog of(List<NpcType> types) {
        Map<String, List<NpcType>> grouped = new HashMap<>();
        Map<Integer, String> nameByTypeId = new HashMap<>();
        for (NpcType type : types) {
            grouped.computeIfAbsent(type.typeName(), n -> new ArrayList<>()).add(type);
            nameByTypeId.put(type.typeId(), type.typeName());
        }
        Map<String, Entry> byName = new HashMap<>();
        for (Map.Entry<String, List<NpcType>> entry : grouped.entrySet()) {
            NpcType best = null;
            SpawnClass bestClass = null;
            List<Integer> typeIds = new ArrayList<>();
            for (NpcType type : entry.getValue()) {
                typeIds.add(type.typeId());
                SpawnClass spawnClass = SpawnClass.fromGroupName(type.groupName());
                if (best == null || spawnClass.ordinal() > bestClass.ordinal()) {
                    best = type;
                    bestClass = spawnClass;
                }
            }
            byName.put(entry.getKey(), new Entry(entry.getKey(), best.groupName(), bestClass, List.copyOf(typeIds)));
        }
        return new NpcCatalog(byName, nameByTypeId);
    }

    public boolean isEmpty() {
        return byName.isEmpty();
    }

    public int size() {
        return nameByTypeId.size();
    }

    public Optional<Entry> find(String npcName) {
        return Optional.ofNullable(byName.get(npcName));
    }

    public SpawnClass spawnClassOf(String npcName) {
        Entry entry = byName.get(npcName);
        return entry == null ? SpawnClass.OTHER : entry.spawnClass();
    }

    public Optional<String> nameForTypeId(int typeId) {
        return Optional.ofNullable(nameByTypeId.get(typeId));
    }

    public Set<String> officerNames() {
        Set<String> names = new TreeSet<>();
        for (Entry entry : byName.values()) {
            if (entry.spawnClass() == SpawnClass.OFFICER) {
                names.add(entry.name());
            }
        }
        return names;
    }

    public Optional<String> factionLabelFor(String npcName) {
        Entry entry = byName.get(npcName);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.groupName().startsWith("Mission ")) {
            return Optional.of(MISSIONS_LABEL);
        }
        String label = GameLogKillParser.classifyFaction(entry.groupName());
        return GameLogKillParser.OTHER_FACTION.equals(label) ? Optional.empty() : Optional.of(label);
    }
}
