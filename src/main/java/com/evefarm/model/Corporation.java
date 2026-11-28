package com.evefarm.model;

public record Corporation(long corporationId, String name, String ticker) {

    public String label() {
        return ticker == null || ticker.isBlank() ? name : name + " [" + ticker + "]";
    }
}
