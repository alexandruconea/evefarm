package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.ui.PhosphorIcons.Hue;

import javax.swing.Icon;

public final class LpStoreTabs extends SubTabs {

    public LpStoreTabs(AppContext appContext) {
        super(appContext.settingsDao, "lpStore",
                new Page("offers", "Offers", icon("storefront"), new LpStorePanel(appContext)),
                new Page("purchases", "Purchases", icon("shopping-cart"), new LpPurchasesPanel(appContext),
                        "What you bought in LP stores and the price you need to sell it without a loss"),
                new Page("lpPerDay", "LP per Day", icon("calendar-dots"), new LpProductionPanel(appContext),
                        "The LP your characters earn each day, as a calendar"),
                new Page("report", "Report", icon("file-text"), new MissionReportPanel(appContext),
                        "The LP and the ISK from missions of a day, a week or a month"));
    }

    private static Icon icon(String name) {
        return PhosphorIcons.menu(name, Hue.AMBER);
    }
}
