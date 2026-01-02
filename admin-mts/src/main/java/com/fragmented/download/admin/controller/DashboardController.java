package com.fragmented.download.admin.controller;

import com.fragmented.download.admin.model.ServerStats;
import com.fragmented.download.admin.service.OriginServerService;
import com.fragmented.download.admin.service.TrackerService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dashboard Controller
 * Displays overview statistics from both Origin Server and P2P Tracker
 */
public class DashboardController {

    @FXML
    private Label originFilesLabel;
    @FXML
    private Label originSizeLabel;
    @FXML
    private Label originDiskLabel;
    @FXML
    private Label trackerFilesLabel;
    @FXML
    private Label trackerPeersLabel;
    @FXML
    private Label trackerStatusLabel;
    @FXML
    private Label systemInfoLabel;
    
    // Metrics Cards
    @FXML
    private Label totalFilesCard;
    @FXML
    private Label totalFilesSubtext;
    @FXML
    private Label activePeersCard;
    @FXML
    private Label activePeersSubtext;
    @FXML
    private Label storageUsedCard;
    @FXML
    private Label storageUsedSubtext;
    @FXML
    private Label activeSwarmsCard;
    @FXML
    private Label activeSwarmsSubtext;

    private final OriginServerService originService = new OriginServerService();
    private final TrackerService trackerService = new TrackerService();
    
    // Flag to show startup notification only once
    private final AtomicBoolean startupNotificationShown = new AtomicBoolean(false);

    @FXML
    private void initialize() {
        refreshAll();
        // Check servers after a short delay and show notification if needed
        new Thread(() -> {
            try {
                Thread.sleep(1500); // Wait for initial refresh to complete
                checkServersAndNotify();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    
    /**
     * Check if servers are running and show notification if not
     * Shows separate notifications for each server
     */
    private void checkServersAndNotify() {
        if (startupNotificationShown.get()) {
            return; // Already shown
        }
        
        boolean originRunning = false;
        boolean trackerRunning = false;
        
        // Check Origin Server
        try {
            originService.getStats();
            originRunning = true;
        } catch (java.net.ConnectException e) {
            originRunning = false;
        } catch (java.net.SocketTimeoutException e) {
            originRunning = false;
        } catch (java.net.UnknownHostException e) {
            originRunning = false;
        } catch (Exception e) {
            // Other exceptions - assume server is not running
            originRunning = false;
        }
        
        // Check P2P Tracker
        try {
            trackerService.getStats();
            trackerRunning = true;
        } catch (java.net.ConnectException e) {
            trackerRunning = false;
        } catch (java.net.SocketTimeoutException e) {
            trackerRunning = false;
        } catch (java.net.UnknownHostException e) {
            trackerRunning = false;
        } catch (Exception e) {
            // Other exceptions - assume server is not running
            trackerRunning = false;
        }
        
        // Copy to final variables for lambda
        final boolean finalOriginRunning = originRunning;
        final boolean finalTrackerRunning = trackerRunning;
        
        // Show separate notifications for each server
        Platform.runLater(() -> {
            // Show Origin Server notification first if not running
            if (!finalOriginRunning) {
                showOriginServerNotification();
            }
            
            // Show P2P Tracker notification after a short delay if not running
            if (!finalTrackerRunning) {
                PauseTransition delay = new PauseTransition(Duration.millis(500));
                delay.setOnFinished(e -> {
                    // Wrap in Platform.runLater to avoid IllegalStateException
                    Platform.runLater(() -> showTrackerServerNotification());
                });
                delay.play();
            }
            
            // Mark as shown if any server is not running
            if (!finalOriginRunning || !finalTrackerRunning) {
                startupNotificationShown.set(true);
            }
        });
    }
    
    /**
     * Show notification for Origin Server
     */
    private void showOriginServerNotification() {
        String message = "⚠️ Origin Server chưa được khởi động!\n\n" +
                        "Port: 8080\n\n" +
                        "💡 Sau khi khởi động, nhấn nút 'Refresh' để cập nhật dữ liệu.";
        
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Origin Server - Chưa Chạy");
        alert.setHeaderText("Origin Server (Port 8080)");
        alert.setContentText(message);
        alert.setWidth(450);
        alert.showAndWait();
    }
    
    /**
     * Show notification for P2P Tracker
     */
    private void showTrackerServerNotification() {
        String message = "⚠️ P2P Tracker chưa được khởi động!\n\n" +
                        "Port: 8081\n\n" +
                        "💡 Sau khi khởi động, nhấn nút 'Refresh' để cập nhật dữ liệu.";
        
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("P2P Tracker - Chưa Chạy");
        alert.setHeaderText("P2P Tracker (Port 8081)");
        alert.setContentText(message);
        alert.setWidth(450);
        alert.showAndWait();
    }

    @FXML
    private void refreshOriginStats() {
        new Thread(() -> {
            try {
                ServerStats stats = originService.getStats();
                Platform.runLater(() -> {
                    originFilesLabel.setText("Files: " + stats.getTotalFiles());
                    originSizeLabel.setText("Total Size: " + stats.getTotalSizeFormatted());
                    originDiskLabel.setText(String.format("Disk: %.1f%% used (%s / %s)",
                            stats.getDiskUsagePercent(),
                            stats.getDiskTotalFormatted(),
                            stats.getDiskFreeFormatted()));
                    
                    // Update metrics cards
                    if (totalFilesCard != null) {
                        totalFilesCard.setText(String.valueOf(stats.getTotalFiles()));
                    }
                    if (storageUsedCard != null) {
                        storageUsedCard.setText(stats.getDiskTotalFormatted());
                    }
                    if (storageUsedSubtext != null) {
                        storageUsedSubtext.setText(String.format("%.1f%% of %s", 
                                stats.getDiskUsagePercent(), 
                                stats.getDiskTotalFormatted()));
                    }
                });
            } catch (java.net.ConnectException e) {
                Platform.runLater(() -> {
                    originFilesLabel.setText("Status: Server not running");
                    originSizeLabel.setText("Please start Origin Server");
                    originDiskLabel.setText("Port: 8080");
                    
                    // Reset metrics cards
                    if (totalFilesCard != null) totalFilesCard.setText("-");
                    if (storageUsedCard != null) storageUsedCard.setText("-");
                    if (storageUsedSubtext != null) storageUsedSubtext.setText("Server offline");
                });
                // Reset flag so notification can be shown again if user starts server
                startupNotificationShown.set(false);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.length() > 50) {
                        errorMsg = errorMsg.substring(0, 50) + "...";
                    }
                    originFilesLabel.setText("Error: " + errorMsg);
                });
            }
        }).start();
    }

    @FXML
    private void refreshTrackerStats() {
        new Thread(() -> {
            try {
                Map<String, Object> stats = trackerService.getStats();
                Platform.runLater(() -> {
                    int totalFiles = stats.containsKey("totalFiles") ? 
                            ((Double) stats.get("totalFiles")).intValue() : 0;
                    int totalPeers = stats.containsKey("totalPeers") ? 
                            ((Double) stats.get("totalPeers")).intValue() : 0;
                    
                    trackerFilesLabel.setText("Active Files: " + totalFiles);
                    trackerPeersLabel.setText("Total Peers: " + totalPeers);
                    trackerStatusLabel.setText("Status: Online");
                    
                    // Update metrics cards
                    if (activePeersCard != null) {
                        activePeersCard.setText(String.valueOf(totalPeers));
                    }
                    if (activeSwarmsCard != null) {
                        activeSwarmsCard.setText(String.valueOf(totalFiles));
                    }
                });
            } catch (java.net.ConnectException e) {
                Platform.runLater(() -> {
                    trackerFilesLabel.setText("Status: Server not running");
                    trackerPeersLabel.setText("Please start P2P Tracker");
                    trackerStatusLabel.setText("Port: 8081");
                    
                    // Reset metrics cards
                    if (activePeersCard != null) activePeersCard.setText("-");
                    if (activeSwarmsCard != null) activeSwarmsCard.setText("-");
                });
                // Reset flag so notification can be shown again if user starts server
                startupNotificationShown.set(false);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.length() > 50) {
                        errorMsg = errorMsg.substring(0, 50) + "...";
                    }
                    trackerStatusLabel.setText("Status: Offline - " + errorMsg);
                });
            }
        }).start();
    }

    private void refreshAll() {
        refreshOriginStats();
        refreshTrackerStats();
        updateSystemInfo();
    }

    private void updateSystemInfo() {
        String info = String.format(
            "Java Version: %s\nOS: %s %s\nAdmin Panel Version: 1.0",
            System.getProperty("java.version"),
            System.getProperty("os.name"),
            System.getProperty("os.version")
        );
        systemInfoLabel.setText(info);
    }
}

