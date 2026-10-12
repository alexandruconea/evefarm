package com.evefarm.auth;

final class LoginRejectedException extends IllegalStateException {

    LoginRejectedException() {
        super("EVE didn't accept the login - try again");
    }
}
