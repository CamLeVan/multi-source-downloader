package com.fragmented.download.javafx.controller;

import com.fragmented.download.javafx.MainApp;
import com.fragmented.download.javafx.model.DownloadTask;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.util.Duration;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.List;

/**
 * DashboardController với UI hoàn chỉnh
 * Hỗ trợ: chọn file, nhập custom file, fetch file list từ server
 */
public class DashboardController {

    @FXML
    private ListView<DownloadTask> downloadListView;

    @FXML
    private ComboBox<String> fileComboBox;

    @FXML
    private TextField fileNameField;

    @FXML
    private Button addDownloadButton;

    @FXML
    private Button refreshButton;

    @FXML
    private Label statusLabel;

    private ObservableList<DownloadTask> downloadTasks;
    private MainApp mainApp;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();

    public void initialize() {
        // Set up a timeline to update the progress bars periodically.
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(0), event -> updateProgress()),
                new KeyFrame(Duration.seconds(1))
        );
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

        // Fetch available files khi khởi động
        fetchAvailableFiles();
    }

    public void setMainApp(MainApp mainApp) {
        this.mainApp = mainApp;
    }

    public void setDownloadTasks(ObservableList<DownloadTask> downloadTasks) {
        this.downloadTasks = downloadTasks;
        downloadListView.setItems(downloadTasks);
        downloadListView.setCellFactory(param -> new DownloadCellController());
        setupDragAndDrop();
    }

    /**
     * Tuần 6: Update progress cho tất cả downloads
     */
    private void updateProgress() {
        if (downloadTasks != null) {
            downloadTasks.forEach(DownloadTask::updateProgress);
        }
    }

    /**
     * Tuần 6: Drag & Drop reordering trong ListView
     */
    private void setupDragAndDrop() {
        downloadListView.setOnDragDetected(event -> {
            if (downloadListView.getSelectionModel().getSelectedItem() == null) {
                return;
            }
            Dragboard dragboard = downloadListView.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(downloadListView.getSelectionModel().getSelectedIndex()));
            dragboard.setContent(content);
            event.consume();
        });

        downloadListView.setOnDragOver(event -> {
            if (event.getGestureSource() != downloadListView || !event.getDragboard().hasString()) {
                return;
            }
            event.acceptTransferModes(TransferMode.MOVE);
            event.consume();
        });

        downloadListView.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                int draggedIndex = Integer.parseInt(db.getString());
                int dropIndex = downloadListView.getSelectionModel().getSelectedIndex();

                DownloadTask draggedTask = downloadTasks.remove(draggedIndex);
                downloadTasks.add(dropIndex, draggedTask);

                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    /**
     * Fetch danh sách files có sẵn từ Origin Server
     */
    private void fetchAvailableFiles() {
        new Thread(() -> {
            try {
                String originUrl = mainApp.getOriginServerUrl();
                String listUrl = originUrl + "/files/list";
                
                Request request = new Request.Builder().url(listUrl).build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        List<String> files = gson.fromJson(json, new TypeToken<List<String>>(){}.getType());
                        
                        // Update UI on JavaFX thread
                        javafx.application.Platform.runLater(() -> {
                            fileComboBox.setItems(FXCollections.observableArrayList(files));
                            statusLabel.setText("Found " + files.size() + " files on server");
                        });
                    }
                }
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Error: Could not fetch file list");
                });
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Handler cho button "Refresh"
     */
    @FXML
    private void onRefreshFiles() {
        statusLabel.setText("Refreshing file list...");
        fetchAvailableFiles();
    }

    /**
     * Handler cho button "Add Download"
     */
    @FXML
    private void onAddDownload() {
        String fileName = null;
        
        // Ưu tiên lấy từ TextField
        if (fileNameField.getText() != null && !fileNameField.getText().trim().isEmpty()) {
            fileName = fileNameField.getText().trim();
        }
        // Nếu không có, lấy từ ComboBox
        else if (fileComboBox.getValue() != null) {
            fileName = fileComboBox.getValue();
        }
        
        // Validate
        if (fileName == null || fileName.isEmpty()) {
            statusLabel.setText("Error: Please select or enter a file name");
            showAlert("Error", "Please select a file from the list or enter a file name");
            return;
        }
        
        // Build manifest URL
        String originUrl = mainApp.getOriginServerUrl();
        String manifestUrl = originUrl + "/manifest/" + fileName;
        
        // Start download
        statusLabel.setText("Starting download: " + fileName);
        mainApp.startDownload(manifestUrl);
        
        // Clear input
        fileNameField.clear();
        fileComboBox.getSelectionModel().clearSelection();
        
        statusLabel.setText("Added: " + fileName + " to download queue");
    }

    /**
     * Helper method để hiển thị alert
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
