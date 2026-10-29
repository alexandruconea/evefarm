package com.evefarm.util;

public final class ForeignSecretException extends IllegalStateException {

    public ForeignSecretException(String message) {
        super(message);
    }

    public ForeignSecretException(String message, Throwable cause) {
        super(message, cause);
    }
}
