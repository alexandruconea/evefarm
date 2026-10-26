package com.evefarm.model;

// A rejected login keeps no usable refresh token: EVE refused it, so the character has to be added again.
public record TokenRecord(String refreshToken, boolean loginRejected) {

    public static TokenRecord active(String refreshToken) {
        return new TokenRecord(refreshToken, false);
    }

    public static TokenRecord rejected() {
        return new TokenRecord(null, true);
    }

    @Override
    public String toString() {
        return "TokenRecord[loginRejected=" + loginRejected + "]";
    }
}
