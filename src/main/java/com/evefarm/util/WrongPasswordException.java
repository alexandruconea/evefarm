package com.evefarm.util;

public final class WrongPasswordException extends IllegalStateException {

    public WrongPasswordException() {
        super("The backup password doesn't match.");
    }
}
