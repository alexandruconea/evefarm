package com.evefarm.auth;

// EVE refused the character's login; nothing works for it until it is added again.
public final class LoginExpiredException extends IllegalStateException {

    public LoginExpiredException(String characterName) {
        super("Add " + characterName + " again (File > Characters... > Add Character...) - EVE no longer accepts "
                + "its login.");
    }
}
