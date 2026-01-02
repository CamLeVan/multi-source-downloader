package com.fragmented.download.admin.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;

/**
 * Main dashboard controller
 * Manages the tab pane and overall application state
 */
public class AdminDashboardController {

    @FXML
    private TabPane mainTabPane;
    
    @FXML
    private Label statusLabel;

    @FXML
    private void initialize() {
        // Set initial status
        updateStatus("Connected");
    }

    public void updateStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText("Status: " + status);
        }
    }

    public TabPane getMainTabPane() {
        return mainTabPane;
    }
}

