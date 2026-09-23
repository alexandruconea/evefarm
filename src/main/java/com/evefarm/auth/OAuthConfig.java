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
            "esi-characters.read_loyalty.v1"
    );

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
