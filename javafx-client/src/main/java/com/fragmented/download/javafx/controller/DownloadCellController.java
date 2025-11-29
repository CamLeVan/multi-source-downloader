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
    private Button playButton;
    
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
    
    @FXML
    private Label detailsLabel;
    
    @FXML
    private Label progressLabel;

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
            
            if (detailsLabel != null) {
                detailsLabel.textProperty().bind(task.detailsProperty());
            }
            
            if (progressLabel != null) {
                progressLabel.textProperty().bind(
                    Bindings.createStringBinding(
                        () -> String.format("%.1f%%", task.getProgress() * 100),
                        task.progressProperty()
                    )
                );
            }
            
            if (playButton != null) {
                // Smart Action Button Logic
                FileType type = detectFileType(task.getFileName());
                playButton.getStyleClass().removeAll("play-button", "open-folder-button", "preview-button");
                
                if (type == FileType.VIDEO || type == FileType.AUDIO) {
                    playButton.setText("▶ Play");
                    playButton.getStyleClass().add("play-button");
                    playButton.disableProperty().bind(task.streamingUrlProperty().isEmpty());
                } else {
                    playButton.setText("📂 Open");
                    playButton.getStyleClass().add("open-folder-button");
                    // Always enable "Open Folder" as the folder exists
                    playButton.disableProperty().unbind();
                    playButton.setDisable(false);
                }
            }
            
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

    @FXML
    private void handlePlay() {
        DownloadTask task = getItem();
        if (task == null) return;
        
        FileType type = detectFileType(task.getFileName());
        
        if (type == FileType.VIDEO || type == FileType.AUDIO) {
            // Streaming Logic
            String url = task.getStreamingUrl();
            if (url != null && !url.isEmpty()) {
                // Copy to clipboard
                javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(url);
                clipboard.setContent(content);
                
                // Show alert
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("Streaming URL");
                alert.setHeaderText("URL Copied to Clipboard!");
                alert.setContentText("You can paste this URL into VLC or any video player:\n\n" + url);
                alert.showAndWait();
            }
        } else {
            // Open Folder Logic
            try {
                String path = task.getScheduler().getLocalFilePath();
                java.io.File file = new java.io.File(path);
                java.io.File parent = file.getParentFile();
                
                if (parent != null && parent.exists()) {
                    java.awt.Desktop.getDesktop().open(parent);
                } else {
                    // Fallback if parent doesn't exist (unlikely)
                    System.err.println("Parent folder does not exist: " + path);
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Show error alert
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Cannot Open Folder");
                alert.setContentText("Failed to open folder: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }

    private enum FileType {
        VIDEO, AUDIO, ARCHIVE, EXECUTABLE, IMAGE, DOCUMENT, OTHER
    }

    private FileType detectFileType(String fileName) {
        if (fileName == null) return FileType.OTHER;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".mov") || lower.endsWith(".webm")) {
            return FileType.VIDEO;
        } else if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac") || lower.endsWith(".aac")) {
            return FileType.AUDIO;
        } else if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") || lower.endsWith(".tar") || lower.endsWith(".gz")) {
            return FileType.ARCHIVE;
        } else if (lower.endsWith(".exe") || lower.endsWith(".msi") || lower.endsWith(".bat") || lower.endsWith(".sh")) {
            return FileType.EXECUTABLE;
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".bmp")) {
            return FileType.IMAGE;
        } else if (lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".txt")) {
            return FileType.DOCUMENT;
        }
        return FileType.OTHER;
    }
}

