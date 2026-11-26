package com.evefarm.ui;

import com.evefarm.AppContext;

import javax.swing.Icon;
import java.util.function.Consumer;

public final class LpStoreTabs extends SubTabs {

    LpStoreTabs(AppContext appContext, Consumer<StandingTarget> showStandings) {
        super(appContext.settingsDao, "lpStore",
                new Page("offers", "Offers", icon("store"), new LpStorePanel(appContext)),
                new Page("purchases", "Purchases", icon("cart-shopping"), new LpPurchasesPanel(appContext),
                        "What you bought in LP stores and the price you need to sell it without a loss"),
                new Page("lpPerDay", "LP per Day", icon("calendar-days"), new LpProductionPanel(appContext),
                        "The LP your characters earn each day, as a calendar"),
                new Page("report", "Report", icon("file-lines"), new MissionReportPanel(appContext, showStandings),
                        "The LP and the ISK from missions of a day, a week, a month, a year or all time"));
    }

    private static Icon icon(String name) {
        return FontAwesome.menu(name);
    }
}
