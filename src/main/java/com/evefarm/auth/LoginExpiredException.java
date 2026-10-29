package com.evefarm.auth;

public final class LoginExpiredException extends IllegalStateException {

    public LoginExpiredException(String characterName) {
        super("Add " + characterName + " again (File > Characters... > Add Character...) - EVE no longer accepts "
                + "its login.");
    }
}
