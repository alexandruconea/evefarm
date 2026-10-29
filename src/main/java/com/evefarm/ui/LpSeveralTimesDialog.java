package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.LpOfferRow;
import com.evefarm.model.SellingFees;
import com.evefarm.service.LpOfferDepthService;
import com.evefarm.service.LpOfferDepthService.JitaOrders;
import com.evefarm.service.LpOfferDepthService.Purchase;
import com.evefarm.service.LpOfferDepthService.Result;
import com.evefarm.service.LpOfferDepthService.Sale;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.ui.column.ColumnWidthMemory;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;
import com.evefarm.util.Text;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

final class LpSeveralTimesDialog extends JDialog {

    private static final Logger LOG = Logger.getLogger(LpSeveralTimesDialog.class.getName());
    private static final int MAX_TIMES = 100_000;
    private static final int SCREEN_MARGIN_PX = 40;

    private record SaleRow(String how, Sale sale, String daysToSell) {
    }

    private record Loaded(JitaOrders orders, String outputName) {
    }

    private static final List<ColumnDef<Purchase>> PURCHASE_COLUMNS = List.of(
            new ColumnDef<>("item", "Item", String.class, r -> r.item().itemName(),
                    "What you need to buy"),
            new ColumnDef<>("for", "For", String.class, r -> r.buildMaterial() ? "Build" : "Offer",
                    "Offer: the offer asks for it. Build: a material to build what the blueprint makes"),
            new ColumnDef<>("needed", "Needed", String.class, r -> Text.count(r.needed()),
                    "How many you need for all the offers"),
            new ColumnDef<>("forSale", "For Sale", String.class, r -> Text.count(r.forSale()),
                    "How many are for sale in Jita 4-4 right now"),
            new ColumnDef<>("lowestPrice", "Lowest Price", String.class, r -> isk(r.lowestPrice()),
                    "The lowest sell price in Jita 4-4, the one the LP Store table counts every unit at"),
            new ColumnDef<>("averagePrice", "Average Price", String.class, r -> isk(r.averagePrice()),
                    "What each unit really costs you, buying from the cheapest sell order up"),
            new ColumnDef<>("cost", "Cost", String.class, r -> IskFormatter.format(r.cost()),
                    "What you pay for all of them, cheapest first. When Jita 4-4 hasn't enough, only for what is "
                            + "for sale"),
            new ColumnDef<>("aboveLowest", "Above Lowest", String.class, r -> isk(r.aboveLowest()),
                    "How much more you pay than if all of them sold at the lowest price"),
            new ColumnDef<>("missing", "Missing", String.class,
                    r -> r.enough() ? "" : Text.count(r.needed() - r.bought()),
                    "How many you can't buy in Jita 4-4"));

    private static final List<ColumnDef<SaleRow>> SALE_COLUMNS = List.of(
            new ColumnDef<>("how", "How", String.class, SaleRow::how,
                    "Sell order: you list everything at the lowest sell price in Jita 4-4. Buy orders: you sell right "
                            + "away to the buy orders that reach Jita 4-4, the best price first"),
            new ColumnDef<>("units", "Units", String.class, r -> units(r.sale()),
                    "How many units the offers give, and how many the buy orders take when they don't take all"),
            new ColumnDef<>("price", "Price", String.class, r -> isk(r.sale().unitPrice()),
                    "The lowest sell price, or the average price the buy orders pay for the units they take"),
            new ColumnDef<>("profit", "Profit", String.class, r -> isk(r.sale().profit()),
                    "What you make on all the offers, after the ISK cost and the items you buy"),
            new ColumnDef<>("iskPerLp", "ISK/LP", String.class,
                    r -> LpStoreTableModel.formatIskPerLp(r.sale().iskPerLp()),
                    "Profit divided by the LP of all the offers"),
            new ColumnDef<>("netProfit", "Net Profit", String.class, r -> isk(r.sale().netProfit()),
                    "Profit after the sales tax, and for a sell order the broker fee, your main character pays in "
                            + "Jita 4-4"),
            new ColumnDef<>("netIskPerLp", "Net ISK/LP", String.class,
                    r -> LpStoreTableModel.formatIskPerLp(r.sale().netIskPerLp()),
                    "Net Profit divided by the LP of all the offers"),
            new ColumnDef<>("daysToSell", "Days to Sell", String.class, SaleRow::daysToSell,
                    "For a sell order, how many days of sales in The Forge the units equal. Buy orders pay right "
                            + "away"));

    private final AppContext appContext;
    private final LpOfferRow offer;
    private final SellingFees fees;
    private final ColumnTableModel<Purchase> purchaseModel = new ColumnTableModel<>(PURCHASE_COLUMNS);
    private final ColumnTableModel<SaleRow> saleModel = new ColumnTableModel<>(SALE_COLUMNS);
    private final JTable purchaseTable = new JTable(purchaseModel);
    private final JTable saleTable = new JTable(saleModel);
    private final JSpinner timesSpinner = new JSpinner(new SpinnerNumberModel(1, 1, MAX_TIMES, 1));
    private final JButton refreshButton = new JButton("Refresh", Icons.REFRESH);
    private final JLabel perOfferLabel;
    private final JLabel statusLabel = UiColors.mutedLabel("Reading the orders in Jita 4-4...");
    private final JLabel costLabel = new JLabel(" ");
    private final JTextArea warningArea = new JTextArea();
    private JitaOrders orders;
    private String outputName;
    private int tableWidth = 760;

    private LpSeveralTimesDialog(Window owner, AppContext appContext, LpOfferRow offer, SellingFees fees,
                                 long yourLp) {
        super(owner, "Buy Several Times - " + offer.itemName(), ModalityType.MODELESS);
        this.appContext = appContext;
        this.offer = offer;
        this.fees = fees;
        perOfferLabel = UiColors.mutedLabel(perOfferText(offer, null));
        perOfferLabel.putClientProperty("html.disable", Boolean.TRUE);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUi(yourLp);
        pack();
        setLocationRelativeTo(owner);
        load();
    }

    static void open(Window owner, AppContext appContext, LpOfferRow offer, SellingFees fees, long yourLp) {
        new LpSeveralTimesDialog(owner, appContext, offer, fees, yourLp).setVisible(true);
    }

    private void buildUi(long yourLp) {
        JLabel item = new JLabel(offer.itemName());
        item.putClientProperty("html.disable", Boolean.TRUE);
        item.setFont(item.getFont().deriveFont(Font.BOLD, item.getFont().getSize2D() + 1f));

        String timesTip = "How many times you buy the offer. The items it asks for and the units it gives are "
                + "priced against the orders in Jita 4-4 for all of them together";
        JLabel timesLabel = new JLabel("Times:");
        timesLabel.setToolTipText(timesTip);
        timesSpinner.setToolTipText(timesTip);
        timesSpinner.addChangeListener(e -> recalculate());
        refreshButton.setToolTipText("Read the orders in Jita 4-4 again");
        refreshButton.addActionListener(e -> load());
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
        controls.add(timesLabel);
        controls.add(Box.createHorizontalStrut(6));
        controls.add(timesSpinner);
        controls.add(Box.createHorizontalStrut(8));
        controls.add(UiColors.mutedLabel(lpHint(yourLp, offer.lpCost())));
        controls.add(Box.createHorizontalStrut(12));
        controls.add(refreshButton);

        JPanel header = new JPanel(new GridBagLayout());
        GridBagConstraints h = new GridBagConstraints();
        h.gridx = 0;
        h.weightx = 1;
        h.anchor = GridBagConstraints.WEST;
        h.insets = new Insets(0, 0, 4, 0);
        header.add(item, h);
        header.add(perOfferLabel, h);
        h.insets = new Insets(4, 0, 4, 0);
        header.add(controls, h);
        h.insets = new Insets(0, 0, 0, 0);
        header.add(statusLabel, h);

        TableStyler.style(purchaseTable);
        TableStyler.style(saleTable);
        ColumnWidthMemory.install(purchaseTable, "lpSeveralTimesPurchases", appContext.tableColumnStateDao);
        ColumnWidthMemory.install(saleTable, "lpSeveralTimesSales", appContext.tableColumnStateDao);
        int rowHeight = purchaseTable.getRowHeight();
        int purchaseRows = Math.clamp(offer.requiredItems().size() + offer.buildMaterials().size(), 3, 10);
        purchaseTable.setPreferredScrollableViewportSize(new Dimension(tableWidth, rowHeight * purchaseRows));
        saleTable.setPreferredScrollableViewportSize(new Dimension(tableWidth, saleTable.getRowHeight() * 2));

        costLabel.putClientProperty("html.disable", Boolean.TRUE);
        warningArea.setEditable(false);
        warningArea.setFocusable(false);
        warningArea.setOpaque(false);
        warningArea.setLineWrap(true);
        warningArea.setWrapStyleWord(true);
        warningArea.setFont(costLabel.getFont());
        warningArea.setForeground(UiColors.danger());

        JPanel body = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);
        boolean buysItems = !offer.requiredItems().isEmpty() || !offer.buildMaterials().isEmpty();
        if (buysItems) {
            body.add(boldLabel("What you buy in Jita 4-4"), c);
            c.weighty = 1;
            c.fill = GridBagConstraints.BOTH;
            body.add(new JScrollPane(purchaseTable), c);
            c.weighty = 0;
            c.fill = GridBagConstraints.HORIZONTAL;
        }
        body.add(boldLabel("What you get"), c);
        body.add(new JScrollPane(saleTable), c);
        body.add(costLabel, c);
        body.add(warningArea, c);

        JButton close = new JButton("Close", Icons.CANCEL);
        close.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(UiColors.mutedLabel(tableText(offer)), BorderLayout.CENTER);
        bottom.add(close, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(header, BorderLayout.NORTH);
        content.add(body, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
    }

    private static JLabel boldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private void load() {
        refreshButton.setEnabled(false);
        statusLabel.setText("Reading the orders in Jita 4-4...");
        new SwingWorker<Loaded, Void>() {
            @Override
            protected Loaded doInBackground() {
                return new Loaded(appContext.lpOfferDepthService.read(offer),
                        appContext.typeNameCacheService.resolveType(offer.outputTypeId()).name());
            }

            @Override
            protected void done() {
                refreshButton.setEnabled(true);
                Loaded loaded;
                try {
                    loaded = get();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOG.log(Level.WARNING, "Couldn't read the Jita 4-4 orders for " + offer.itemName(), cause);
                    statusLabel.setText("Couldn't read the orders in Jita 4-4: " + cause.getMessage());
                    return;
                }
                orders = loaded.orders();
                outputName = loaded.outputName();
                perOfferLabel.setText(perOfferText(offer, outputName));
                statusLabel.setText("Orders in Jita 4-4 read at " + DateUtil.formatWithSeconds(orders.readAt())
                        + ". Click Refresh to read them again.");
                recalculate();
            }
        }.execute();
    }

    private void recalculate() {
        if (orders == null) {
            return;
        }
        Result result = LpOfferDepthService.price(offer, ((Number) timesSpinner.getValue()).intValue(), orders, fees);
        purchaseModel.setRows(result.purchases());
        saleModel.setRows(List.of(
                new SaleRow("Sell order", result.sellOrder(), daysToSell(result.sellOrder().units(),
                        offer.dailyVolume())),
                new SaleRow("Buy orders", result.buyOrders(), result.buyOrders().enough() ? "Now" : "")));
        costLabel.setText(costText(result));
        String warnings = warnings(result, outputName);
        warningArea.setText(warnings);
        warningArea.setVisible(!warnings.isEmpty());
        fitTables();
    }

    private void fitTables() {
        TableStyler.packColumns(purchaseTable);
        TableStyler.packColumns(saleTable);
        int width = Math.max(columnsWidth(purchaseTable), columnsWidth(saleTable));
        if (width <= tableWidth) {
            return;
        }
        tableWidth = width;
        purchaseTable.setPreferredScrollableViewportSize(new Dimension(width,
                purchaseTable.getPreferredScrollableViewportSize().height));
        saleTable.setPreferredScrollableViewportSize(new Dimension(width,
                saleTable.getPreferredScrollableViewportSize().height));
        Dimension preferred = getPreferredSize();
        int screenWidth = getGraphicsConfiguration() == null ? preferred.width
                : getGraphicsConfiguration().getBounds().width - SCREEN_MARGIN_PX;
        setSize(Math.max(getWidth(), Math.min(preferred.width, screenWidth)), Math.max(getHeight(), preferred.height));
    }

    private static int columnsWidth(JTable table) {
        int width = 0;
        for (int col = 0; col < table.getColumnCount(); col++) {
            width += table.getColumnModel().getColumn(col).getPreferredWidth();
        }
        return width;
    }

    static String perOfferText(LpOfferRow offer, String outputName) {
        String cost = "One offer costs " + Text.count(offer.lpCost()) + " LP and "
                + IskFormatter.format(offer.iskCost());
        if (outputName == null) {
            return cost;
        }
        return cost + (offer.buildMaterials().isEmpty() ? " and gives " : " and builds ")
                + Text.count(offer.outputUnits()) + " x " + outputName;
    }

    static String lpHint(long yourLp, long lpCost) {
        if (yourLp <= 0 || lpCost <= 0) {
            return "";
        }
        long times = yourLp / lpCost;
        if (times == 0) {
            return "Your LP isn't enough for one";
        }
        return "Your LP is enough for " + Text.count(times);
    }

    static String tableText(LpOfferRow offer) {
        return "The LP Store table shows " + orDash(LpStoreTableModel.formatIskPerLp(offer.iskPerLpSell()))
                + " ISK/LP (Sell) and " + orDash(LpStoreTableModel.formatIskPerLp(offer.iskPerLpBuy()))
                + " ISK/LP (Buy) for one offer, at the lowest and highest price only";
    }

    static String costText(Result result) {
        String cost = "Cost: " + Text.count(result.lp()) + " LP + " + IskFormatter.format(result.isk());
        if (result.purchases().isEmpty()) {
            return cost;
        }
        if (result.otherCost() == null) {
            return cost + ", and Jita 4-4 hasn't all the items";
        }
        return cost + " + " + IskFormatter.format(result.otherCost()) + " for the items = "
                + IskFormatter.format(result.isk() + result.otherCost());
    }

    static String warnings(Result result, String outputName) {
        List<String> lines = new ArrayList<>();
        for (Purchase purchase : result.purchases()) {
            if (!purchase.enough()) {
                String name = purchase.item().itemName();
                lines.add(purchase.forSale() == 0 ? "Nobody sells " + name + " in Jita 4-4."
                        : "Jita 4-4 has only " + Text.count(purchase.forSale()) + " " + name + " for sale, you need "
                                + Text.count(purchase.needed()) + ".");
            }
        }
        if (result.sellOrder().unitPrice() == null) {
            lines.add("Nobody sells " + outputName + " in Jita 4-4, so there is no lowest sell price.");
        }
        Sale buy = result.buyOrders();
        if (!buy.enough()) {
            lines.add(buy.sold() == 0 ? "No buy orders for " + outputName + " reach Jita 4-4."
                    : "The buy orders that reach Jita 4-4 take only " + Text.count(buy.sold()) + " of the "
                            + Text.count(buy.units()) + " " + outputName + ".");
        }
        return String.join("\n", lines);
    }

    static String daysToSell(long units, Double dailyVolume) {
        if (dailyVolume == null) {
            return "";
        }
        return LpStoreTableModel.formatDays(dailyVolume <= 0 ? Double.POSITIVE_INFINITY : units / dailyVolume);
    }

    private static String units(Sale sale) {
        return sale.enough() ? Text.count(sale.units())
                : Text.count(sale.sold()) + " of " + Text.count(sale.units());
    }

    private static String isk(Double value) {
        return value == null ? "" : IskFormatter.format(value);
    }

    private static String orDash(String value) {
        return value.isEmpty() ? "-" : value;
    }
}
