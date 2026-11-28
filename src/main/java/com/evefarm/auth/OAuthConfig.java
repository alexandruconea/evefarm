package com.evefarm.auth;

import java.util.List;

public final class OAuthConfig {

    public static final List<String> SCOPES = List.of(
            "esi-assets.read_assets.v1",
            "esi-wallet.read_character_wallet.v1",
            "esi-clones.read_clones.v1",
            "esi-clones.read_implants.v1",
            "esi-markets.read_character_orders.v1",
            "esi-contracts.read_character_contracts.v1",
            "esi-industry.read_character_jobs.v1",
            "esi-skills.read_skills.v1",
            "esi-universe.read_structures.v1",
            "esi-characters.read_loyalty.v1",
            "esi-location.read_location.v1",
            "esi-location.read_ship_type.v1",
            "esi-industry.read_character_mining.v1",
            "esi-characters.read_standings.v1",
            "esi-skills.read_skillqueue.v1",
            "esi-characters.read_blueprints.v1"
    );

    public static final String CORPORATION_ROLES_SCOPE = "esi-characters.read_corporation_roles.v1";
    public static final String CORPORATION_WALLETS_SCOPE = "esi-wallet.read_corporation_wallets.v1";
    public static final String CORPORATION_ASSETS_SCOPE = "esi-assets.read_corporation_assets.v1";
    public static final String CORPORATION_DIVISIONS_SCOPE = "esi-corporations.read_divisions.v1";
    public static final String CORPORATION_ORDERS_SCOPE = "esi-markets.read_corporation_orders.v1";
    public static final String CORPORATION_CONTRACTS_SCOPE = "esi-contracts.read_corporation_contracts.v1";
    public static final String CORPORATION_JOBS_SCOPE = "esi-industry.read_corporation_jobs.v1";
    public static final String CORPORATION_BLUEPRINTS_SCOPE = "esi-corporations.read_blueprints.v1";

    public static final List<String> CORPORATION_SCOPES = List.of(
            CORPORATION_ROLES_SCOPE,
            CORPORATION_WALLETS_SCOPE,
            CORPORATION_ASSETS_SCOPE,
            CORPORATION_DIVISIONS_SCOPE,
            CORPORATION_ORDERS_SCOPE,
            CORPORATION_CONTRACTS_SCOPE,
            CORPORATION_JOBS_SCOPE,
            CORPORATION_BLUEPRINTS_SCOPE
    );

    public static final String LOCATION_SCOPE = "esi-location.read_location.v1";
    public static final String SHIP_SCOPE = "esi-location.read_ship_type.v1";
    public static final String MINING_SCOPE = "esi-industry.read_character_mining.v1";
    public static final String STANDINGS_SCOPE = "esi-characters.read_standings.v1";
    public static final String SKILL_QUEUE_SCOPE = "esi-skills.read_skillqueue.v1";
    public static final String BLUEPRINTS_SCOPE = "esi-characters.read_blueprints.v1";

    private static final String CLIENT_ID = "87669db38180407d99c4925a7f504b06";
    private static final int REDIRECT_PORT = 8086;

    public static final OAuthConfig DEFAULT = new OAuthConfig(CLIENT_ID, REDIRECT_PORT);

    private final String clientId;
    private final int redirectPort;

    private OAuthConfig(String clientId, int redirectPort) {
        this.clientId = clientId;
        this.redirectPort = redirectPort;
    }

    public String clientId() {
        return clientId;
    }

    public int redirectPort() {
        return redirectPort;
    }

    public String redirectUri() {
        return "http://localhost:" + redirectPort + "/callback";
    }
}
