package com.evefarm.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record CorporationAccess(long characterId, long corporationId, List<String> roles, Instant checkedAt) {

    public static final String DIRECTOR = "Director";
    private static final Set<String> WALLET_ROLES = Set.of(DIRECTOR, "Accountant", "Junior_Accountant");
    private static final Set<String> ORDER_ROLES = Set.of(DIRECTOR, "Accountant", "Trader");
    private static final Set<String> JOB_ROLES = Set.of(DIRECTOR, "Factory_Manager");
    private static final List<String> USEFUL_ROLES = List.of("Accountant", "Junior_Accountant", "Trader",
            "Factory_Manager");

    public CorporationAccess {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public boolean readsWallets() {
        return roles.stream().anyMatch(WALLET_ROLES::contains);
    }

    public boolean readsOrders() {
        return roles.stream().anyMatch(ORDER_ROLES::contains);
    }

    public boolean readsJobs() {
        return roles.stream().anyMatch(JOB_ROLES::contains);
    }

    public boolean readsAssets() {
        return roles.contains(DIRECTOR);
    }

    public String summary() {
        if (roles.contains(DIRECTOR)) {
            return DIRECTOR;
        }
        List<String> useful = USEFUL_ROLES.stream().filter(roles::contains).map(role -> role.replace('_', ' '))
                .toList();
        return useful.isEmpty() ? "Contracts only" : String.join(", ", useful);
    }
}
