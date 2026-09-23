package com.evefarm.ui;

import com.evefarm.AppContext;

public final class IndustryJobsPanel extends javax.swing.JPanel {

    private static final String PANEL_KEY = "industryJobs";

    private final AppContext appContext;
    private final IndustryJobsTableModel tableModel = new IndustryJobsTableModel();
    private DataTablePanelSupport<com.evefarm.model.IndustryJobRow> support;

    public IndustryJobsPanel(AppContext appContext) {
        initComponents();
        this.appContext = appContext;
        postInit();
    }

    private void postInit() {
        support = new DataTablePanelSupport<>(appContext, PANEL_KEY, tableModel, table, filterBarContainer,
                jobCountLabel, "jobs", () -> appContext.industryJobService.getJobRows(null));
        support.init();
    }

    public void onShown() {
        support.reload();
    }

    public void refreshCharacterFilter() {
        support.reload();
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel filterBarContainer;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel jobCountLabel;
    private javax.swing.JTable table;
    // End of variables declaration//GEN-END:variables

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        filterBarContainer = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        table = new javax.swing.JTable();
        jobCountLabel = new javax.swing.JLabel();

        javax.swing.GroupLayout filterBarContainerLayout = new javax.swing.GroupLayout(filterBarContainer);
        filterBarContainer.setLayout(filterBarContainerLayout);
        filterBarContainerLayout.setHorizontalGroup(
            filterBarContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 880, Short.MAX_VALUE)
        );
        filterBarContainerLayout.setVerticalGroup(
            filterBarContainerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 60, Short.MAX_VALUE)
        );

        jScrollPane2.setViewportView(table);

        jobCountLabel.setText("0 jobs");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(filterBarContainer, javax.swing.GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 880, Short.MAX_VALUE)
                    .addComponent(jobCountLabel))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(filterBarContainer, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 380, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jobCountLabel)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents
}
