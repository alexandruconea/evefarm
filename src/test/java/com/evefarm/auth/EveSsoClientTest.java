package com.evefarm.auth;

import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EveSsoClientTest {

    @Test
    void aLoginEveNoLongerAcceptsIsRecognized() {
        IllegalStateException failure = EveSsoClient.failure(400, "{\"error\":\"invalid_grant\","
                + "\"error_description\":\"Invalid refresh token. Token missing/expired.\"}");

        assertInstanceOf(LoginRejectedException.class, failure);
        assertEquals("EVE didn't accept the login - try again", failure.getMessage());
    }

    @Test
    void theCorporationIsOnlyAskedForWhenTicked() {
        EveSsoClient client = new EveSsoClient();
        String personal = client.buildAuthorizeUrl(OAuthConfig.DEFAULT, "state", "challenge", OAuthConfig.SCOPES)
                .toString();
        String withCorporation = client.buildAuthorizeUrl(OAuthConfig.DEFAULT, "state", "challenge",
                Stream.concat(OAuthConfig.SCOPES.stream(), OAuthConfig.CORPORATION_SCOPES.stream()).toList())
                .toString();

        assertFalse(personal.contains("corporation"));
        assertTrue(withCorporation.contains("esi-assets.read_corporation_assets.v1"));
        assertTrue(withCorporation.contains("esi-wallet.read_corporation_wallets.v1"));
    }

    @Test
    void otherFailuresKeepTheirHttpStatus() {
        IllegalStateException badRequest = EveSsoClient.failure(400, "{\"error\":\"invalid_request\"}");
        IllegalStateException serverError = EveSsoClient.failure(500, "{\"error\":\"invalid_grant\"}");
        IllegalStateException notJson = EveSsoClient.failure(400, "<html>Bad Request</html>");

        assertFalse(badRequest instanceof LoginRejectedException);
        assertFalse(serverError instanceof LoginRejectedException, "EVE's own failures are not a rejected login");
        assertFalse(notJson instanceof LoginRejectedException);
        assertEquals("EVE SSO token request failed: HTTP 500", serverError.getMessage());
        assertEquals("EVE SSO token request failed: HTTP 400", notJson.getMessage());
    }
}
