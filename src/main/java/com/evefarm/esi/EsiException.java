package com.evefarm.esi;

public final class EsiException extends RuntimeException {

    private final int statusCode;

    public EsiException(int statusCode, String message) {
        super("ESI request failed with HTTP " + statusCode + ": " + message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
