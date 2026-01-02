package com.fragmented.download.admin.controller;

import com.fragmented.download.admin.model.FileSwarm;
import com.fragmented.download.admin.service.TrackerService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

/**
 * Peer Monitoring Controller
 * Displays P2P tracker information: active swarms, peers, health status
 */
public class PeerMonitoringController {

    @FXML
    private TableView<FileSwarm> swarmsTable;
    @FXML
    private TableColumn<FileSwarm, String> fileIdColumn;
    @FXML
    private TableColumn<FileSwarm, Integer> seedersColumn;
    @FXML
    private TableColumn<FileSwarm, String> peersColumn;
    @FXML
    private TableColumn<FileSwarm, String> healthColumn;
    @FXML
    private TableColumn<FileSwarm, Void> actionsColumn;
    @FXML
    private Label statusLabel;

    private final TrackerService trackerService = new TrackerService();
    private final ObservableList<FileSwarm> swarms = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        setupTable();
        refreshSwarms();
    }

    private void setupTable() {
        fileIdColumn.setCellValueFactory(new PropertyValueFactory<>("fileId"));
        seedersColumn.setCellValueFactory(new PropertyValueFactory<>("seeders"));
        
        peersColumn.setCellFactory(column -> new TableCell<FileSwarm, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    FileSwarm swarm = getTableRow().getItem();
                    setText(String.join(", ", swarm.getPeers()));
                }
            }
        });
        
        healthColumn.setCellValueFactory(new PropertyValueFactory<>("health"));
        healthColumn.setCellFactory(column -> new TableCell<FileSwarm, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    String health = getTableRow().getItem().getHealth();
                    setText(health);
                    if ("Healthy".equals(health)) {
                        setStyle("-fx-text-fill: green;");
                    } else if ("Stable".equals(health)) {
                        setStyle("-fx-text-fill: orange;");
                    } else {
                        setStyle("-fx-text-fill: red;");
                    }
                }
            }
        });

        actionsColumn.setCellFactory(column -> new TableCell<FileSwarm, Void>() {
            private final Button clearBtn = new Button("Clear");

            {
                clearBtn.setOnAction(e -> {
                    FileSwarm swarm = getTableRow().getItem();
                    if (swarm != null) {
                        handleClearFile(swarm.getFileId());
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(clearBtn);
                }
            }
        });

        swarmsTable.setItems(swarms);
    }

    @FXML
    private void refreshSwarms() {
        updateStatus("Loading swarms...");
        new Thread(() -> {
            try {
                List<FileSwarm> swarmList = trackerService.getFileSwarms();
                Platform.runLater(() -> {
                    swarms.setAll(swarmList);
                    updateStatus("Loaded " + swarmList.size() + " active swarms");
                });
            } catch (java.net.ConnectException e) {
                Platform.runLater(() -> {
                    updateStatus("P2P Tracker not running (Port 8081)");
                    swarms.clear();
                    // Don't show error popup on auto-refresh
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    updateStatus("Error: " + e.getMessage());
                    showAlert(Alert.AlertType.WARNING, "Connection Error", 
                            "Failed to load swarms", 
                            "Please ensure P2P Tracker is running on port 8081.\n\n" + e.getMessage());
                });
            }
        }).start();
    }

    private void handleClearFile(String fileId) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Clear");
        confirm.setHeaderText("Clear File Swarm");
        confirm.setContentText("Are you sure you want to clear all peers for: " + fileId + "?");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                updateStatus("Clearing " + fileId + "...");
                new Thread(() -> {
                    try {
                        trackerService.clearFile(fileId);
                        Platform.runLater(() -> {
                            updateStatus("File swarm cleared successfully");
                            refreshSwarms();
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            updateStatus("Clear failed: " + e.getMessage());
                            showAlert(Alert.AlertType.ERROR, "Error", "Clear Failed", e.getMessage());
                        });
                        e.printStackTrace();
                    }
                }).start();
            }
        });
    }

    private void updateStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);
        }
    }

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}

