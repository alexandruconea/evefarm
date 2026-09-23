package com.evefarm.esi;

import com.evefarm.util.AppInfo;

public final class EsiConfig {
    public static final String BASE_URL = "https://esi.evetech.net/latest";
    public static final String DATASOURCE = "tranquility";
    public static final String USER_AGENT = AppInfo.userAgent();

    private EsiConfig() {
    }
}
