package com.evefarm.model;

public record SolarSystem(long systemId, String name, double security, String regionName) {

    public double roundedSecurity() {
        if (security > 0 && security < 0.05) {
            return 0.1;
        }
        return Math.round(security * 10) / 10.0;
    }
}
