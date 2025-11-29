package com.fragmented.download.javafx.controller;

import java.io.IOException;
import java.text.DecimalFormat;

import com.fragmented.download.javafx.model.DownloadTask;
import com.fragmented.download.javafx.model.SourceProgress;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;

/**
 * Tuần 6: Controller cho custom ListView cell
 * Hiển thị thông tin download và Pause/Resume buttons
 */
public class DownloadCellController extends ListCell<DownloadTask> {

    @FXML
    private HBox hBox;

    @FXML
    private Label fileNameLabel;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label statusLabel;

    @FXML
    private Button pauseButton;

    @FXML
    private Button resumeButton;
    
    @FXML
    private ProgressBar originProgressBar;
    
    @FXML
    private ProgressBar mirrorProgressBar;
    
    @FXML
    private ProgressBar peerProgressBar;
    
    @FXML
    private Label originLabel;
    
    @FXML
    private Label mirrorLabel;
    
    @FXML
    private Label peerLabel;

    private FXMLLoader fxmlLoader;
    private static final DecimalFormat BYTES_FORMAT = new DecimalFormat("#,##0.00");

    @Override
    protected void updateItem(DownloadTask task, boolean empty) {
        super.updateItem(task, empty);

        if (empty || task == null) {
            setText(null);
            setGraphic(null);
        } else {
            if (fxmlLoader == null) {
                fxmlLoader = new FXMLLoader(getClass().getResource("/fxml/DownloadCell.fxml"));
                fxmlLoader.setController(this);
                try {
                    fxmlLoader.load();
                } catch (IOException e) {
                    e.printStackTrace();
                    setText("Error loading cell: " + e.getMessage());
                    return;
                }
            }

            // Check if FXML loaded successfully
            if (fileNameLabel == null || progressBar == null || statusLabel == null || hBox == null) {
                setText("Error: FXML not loaded properly");
                return;
            }

            // Quick UI tweak: đảm bảo progress bars hiển thị rõ (màu xanh)
            // Nếu muốn thay đổi styling lâu dài, chỉnh CSS tại /resources/styles/dashboard.css
            try {
                progressBar.setStyle("-fx-accent: #00b300;");
                originProgressBar.setStyle("-fx-accent: #00b300;");
                mirrorProgressBar.setStyle("-fx-accent: #00b300;");
                peerProgressBar.setStyle("-fx-accent: #00b300;");
            } catch (Exception e) {
                // Ignore if any progress bars are null or style not supported on platform
            }

            // Tuần 6: Bind UI elements to DownloadTask properties
            fileNameLabel.textProperty().bind(task.fileNameProperty());
            progressBar.progressProperty().bind(task.progressProperty());
            statusLabel.textProperty().bind(task.statusProperty());
            
            // Bind per-source progress bars
            bindSourceProgress(task);

            setGraphic(hBox);
        }
    }
    
    /**
     * Bind progress bars và labels cho từng source
     */
    private void bindSourceProgress(DownloadTask task) {
        // Tìm source progress cho từng type
        SourceProgress originProgress = findSourceProgress(task, SourceProgress.SourceType.ORIGIN);
        SourceProgress mirrorProgress = findSourceProgress(task, SourceProgress.SourceType.MIRROR);
        SourceProgress peerProgress = findSourceProgress(task, SourceProgress.SourceType.PEER);
        
        // Bind origin progress
        if (originProgress != null) {
            // Progress = bytes downloaded / total file size (approximate)
            originProgressBar.progressProperty().bind(
                Bindings.createDoubleBinding(
                    () -> {
                        long totalBytes = task.getScheduler().getManifest().getFileSize();
                        return totalBytes > 0 ? (double) originProgress.getBytesDownloaded() / totalBytes : 0.0;
                    },
                    originProgress.bytesDownloadedProperty()
                )
            );
            originLabel.textProperty().bind(
                Bindings.createStringBinding(
                    () -> formatBytes(originProgress.getBytesDownloaded()),
                    originProgress.bytesDownloadedProperty()
                )
            );
        }
        
        // Bind mirror progress
        if (mirrorProgress != null) {
            mirrorProgressBar.progressProperty().bind(
                Bindings.createDoubleBinding(
                    () -> {
                        long totalBytes = task.getScheduler().getManifest().getFileSize();
                        return totalBytes > 0 ? (double) mirrorProgress.getBytesDownloaded() / totalBytes : 0.0;
                    },
                    mirrorProgress.bytesDownloadedProperty()
                )
            );
            mirrorLabel.textProperty().bind(
                Bindings.createStringBinding(
                    () -> formatBytes(mirrorProgress.getBytesDownloaded()),
                    mirrorProgress.bytesDownloadedProperty()
                )
            );
        }
        
        // Bind peer progress (sum of all peers)
        // Listen to ObservableList changes và update binding khi có peer mới
        task.getSourceProgresses().addListener((javafx.collections.ListChangeListener<SourceProgress>) change -> {
            // Re-bind khi list thay đổi
            updatePeerBinding(task);
        });
        updatePeerBinding(task);
    }
    
    private SourceProgress findSourceProgress(DownloadTask task, SourceProgress.SourceType type) {
        return task.getSourceProgresses().stream()
            .filter(sp -> sp.getSourceType() == type)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Update binding cho peer progress bars
     */
    private void updatePeerBinding(DownloadTask task) {
        // Unbind cũ trước
        peerProgressBar.progressProperty().unbind();
        peerLabel.textProperty().unbind();
        
        // Tạo binding động từ ObservableList
        // Binding sẽ tự động update khi list hoặc properties thay đổi
        peerProgressBar.progressProperty().bind(
            Bindings.createDoubleBinding(
                () -> {
                    long totalBytes = task.getScheduler().getManifest().getFileSize();
                    long peerBytes = task.getSourceProgresses().stream()
                        .filter(sp -> sp.getSourceType() == SourceProgress.SourceType.PEER)
                        .mapToLong(SourceProgress::getBytesDownloaded)
                        .sum();
                    return totalBytes > 0 ? (double) peerBytes / totalBytes : 0.0;
                },
                task.getSourceProgresses()
            )
        );
        
        peerLabel.textProperty().bind(
            Bindings.createStringBinding(
                () -> {
                    long peerBytes = task.getSourceProgresses().stream()
                        .filter(sp -> sp.getSourceType() == SourceProgress.SourceType.PEER)
                        .mapToLong(SourceProgress::getBytesDownloaded)
                        .sum();
                    return formatBytes(peerBytes);
                },
                task.getSourceProgresses()
            )
        );
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return BYTES_FORMAT.format(bytes / 1024.0) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return BYTES_FORMAT.format(bytes / (1024.0 * 1024.0)) + " MB";
        } else {
            return BYTES_FORMAT.format(bytes / (1024.0 * 1024.0 * 1024.0)) + " GB";
        }
    }

    /**
     * Tuần 6: Xử lý nút Pause - gọi Scheduler.pause()
     */
    @FXML
    private void handlePause() {
        getItem().getScheduler().pause();
        pauseButton.setDisable(true);
        resumeButton.setDisable(false);
        getItem().statusProperty().set("Paused");
    }

    /**
     * Tuần 6: Xử lý nút Resume - gọi Scheduler.resume()
     */
    @FXML
    private void handleResume() {
        getItem().getScheduler().resume();
        pauseButton.setDisable(false);
        resumeButton.setDisable(true);
        getItem().statusProperty().set("Downloading");
    }
}

