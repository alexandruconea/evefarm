package com.evefarm.ui;

import com.evefarm.model.JournalRow;
import com.evefarm.service.ItemIconService.EveImage;
import com.evefarm.service.StandingService;

record EveEntity(String name, Kind kind, Long id) implements Comparable<EveEntity> {

    enum Kind {
        CHARACTER, AGENT, CORPORATION, ALLIANCE, FACTION, OTHER;

        static Kind ofStanding(String fromType) {
            if (StandingService.FACTION.equals(fromType)) {
                return FACTION;
            }
            return StandingService.CORPORATION.equals(fromType) ? CORPORATION : AGENT;
        }

        static Kind ofCategory(String category) {
            if (category == null) {
                return OTHER;
            }
            return switch (category) {
                case "character" -> CHARACTER;
                case "corporation" -> CORPORATION;
                case "alliance" -> ALLIANCE;
                case "faction" -> FACTION;
                default -> OTHER;
            };
        }
    }

    static EveEntity character(String name, long characterId) {
        return new EveEntity(name, Kind.CHARACTER, characterId);
    }

    static EveEntity party(JournalRow.Party party) {
        return new EveEntity(party.name(), Kind.ofCategory(party.category()), party.id());
    }

    boolean linked() {
        return id != null && name != null && !name.isBlank();
    }

    EveImage image() {
        if (id == null) {
            return null;
        }
        return switch (kind) {
            case CHARACTER, AGENT -> EveImage.portrait(id);
            case CORPORATION, FACTION -> EveImage.logo(id);
            case ALLIANCE -> EveImage.allianceLogo(id);
            case OTHER -> null;
        };
    }

    @Override
    public int compareTo(EveEntity other) {
        return String.CASE_INSENSITIVE_ORDER.compare(toString(), other.toString());
    }

    @Override
    public String toString() {
        return name == null ? "" : name;
    }
}
