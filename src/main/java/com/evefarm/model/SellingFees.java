package com.evefarm.model;

public record SellingFees(String characterName, double salesTax, double brokerFee) {

    public double sellOrderRate() {
        return salesTax + brokerFee;
    }
}
