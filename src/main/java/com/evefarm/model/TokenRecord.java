package com.evefarm.model;

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
