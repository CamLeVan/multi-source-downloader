package com.fragmented.download.javafx.controller;

import com.fragmented.download.javafx.MainApp;
import com.fragmented.download.javafx.model.DownloadTask;
import com.fragmented.download.javafx.model.FileInfo;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.util.List;

/**
 * DashboardController với UI hoàn chỉnh
 * Hỗ trợ: chọn file, nhập custom file, fetch file list từ server.
 * Update: Hỗ trợ Split View và Media Player.
 */
public class DashboardController {

    @FXML
    private javafx.scene.media.MediaView mediaPlayerView;
    @FXML
    private VBox playerPlaceholder;
    @FXML
    private Label nowPlayingLabel;

    @FXML
    private ListView<DownloadTask> downloadListView;
    @FXML
    private ListView<FileInfo> fileListView;

    // UI Components for Search/Filter
    @FXML
    private TextField searchField;
    @FXML
    private Label fileCountLabel;
    @FXML
    private Label statusLabel;

    // Filter Buttons
    @FXML
    private Button filterAllButton;
    @FXML
    private Button filterVideoButton;
    @FXML
    private Button filterArchiveButton;

    // Progress UI for Streaming
    @FXML
    private ProgressBar videoProgressBar;
    @FXML
    private Label videoProgressLabel;
    @FXML
    private Label videoSpeedLabel;
    @FXML
    private javafx.scene.layout.VBox downloadProgressContainer;

    // Legacy fields (kept for safety if FXML still references them)
    @FXML
    private TextField fileNameField;
    @FXML
    private ComboBox<String> fileComboBox;
    @FXML
    private ComboBox<String> sortComboBox;
    @FXML
    private Button addDownloadButton;
    @FXML
    private Button refreshButton;

    // Data & Logic
    private ObservableList<DownloadTask> downloadTasks;
    private MainApp mainApp;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

    // Data stores
    private ObservableList<FileInfo> allFileInfos = FXCollections.observableArrayList();
    private String selectedCategory = "All";

    // Media Player reference
    private javafx.scene.media.MediaPlayer currentMediaPlayer;

    /**
     * Initialize UI logic
     */
    public void initialize() {
        // Progress updater loop
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(0), event -> updateProgress()),
                new KeyFrame(Duration.seconds(1)));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

        // Setup UI components
        setupFileListView();
        setupSearchAndFilter();

        // Responsive Media Player
        if (mediaPlayerView != null) {
            // Bind to parent container for responsive sizing
            mediaPlayerView.fitWidthProperty().bind(((StackPane) mediaPlayerView.getParent()).widthProperty());
            mediaPlayerView.fitHeightProperty().bind(((StackPane) mediaPlayerView.getParent()).heightProperty());
        }
    }

    // Retry counter cho video playback
    private int videoPlaybackRetryCount = 0;
    private static final int MAX_VIDEO_RETRIES = 3;

    /**
     * Start Playing Video Stream
     * Called by MainApp when streaming server is ready
     */
    public void playVideoStream(String streamUrl, String fileName) {
        playVideoStream(streamUrl, fileName, 0);
    }

    /**
     * Start Playing Video Stream với retry logic
     * @param streamUrl URL của stream
     * @param fileName Tên file
     * @param retryCount Số lần đã retry
     */
    private void playVideoStream(String streamUrl, String fileName, int retryCount) {
        if (streamUrl == null)
            return;

        // Simple check: only play if it looks like a video
        // STRICT: JavaFX only plays .mp4 properly for Http Streaming
        if (!fileName.toLowerCase().endsWith(".mp4")) {
            System.out.println("[UI] Skipping playback for non-MP4: " + fileName);
            if (nowPlayingLabel != null) {
                nowPlayingLabel.setText("Preview unavailable (Format not supported)");
                nowPlayingLabel.setStyle("-fx-text-fill: #e67e22;");
            }
            return;
        }

        System.out.println("[UI] Attempting to play stream: " + streamUrl + (retryCount > 0 ? " (Retry " + retryCount + ")" : ""));

        // Stop previous player
        if (currentMediaPlayer != null) {
            currentMediaPlayer.stop();
            currentMediaPlayer.dispose();
            currentMediaPlayer = null;
        }

        try {
            javafx.scene.media.Media media = new javafx.scene.media.Media(streamUrl);
            currentMediaPlayer = new javafx.scene.media.MediaPlayer(media);
            mediaPlayerView.setMediaPlayer(currentMediaPlayer);

            // UI Update
            if (playerPlaceholder != null)
                playerPlaceholder.setVisible(false);
            if (nowPlayingLabel != null) {
                if (retryCount > 0) {
                    nowPlayingLabel.setText("Retrying playback: " + fileName + " (Attempt " + (retryCount + 1) + ")");
                    nowPlayingLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
                } else {
                    nowPlayingLabel.setText("Now Playing: " + fileName);
                    nowPlayingLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;");
                }
            }

            // Handle successful initialization
            currentMediaPlayer.setOnReady(() -> {
                System.out.println("[UI] Media player ready for: " + fileName);
                if (nowPlayingLabel != null) {
                    nowPlayingLabel.setText("Now Playing: " + fileName);
                    nowPlayingLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;");
                }
                if (statusLabel != null) {
                    statusLabel.setText("Video ready for playback");
                }
                videoPlaybackRetryCount = 0; // Reset retry count on success
            });

            // Handle errors với retry logic
            currentMediaPlayer.setOnError(() -> {
                String err = currentMediaPlayer.getError() != null ? currentMediaPlayer.getError().toString() : "Unknown error";
                System.err.println("Media Error: " + err);

                // Retry nếu là lỗi network/connection và chưa vượt quá số lần retry
                boolean shouldRetry = (err.contains("ERROR_MEDIA_INVALID") || 
                                      err.contains("UNKNOWN") || 
                                      err.contains("NETWORK")) && 
                                     retryCount < MAX_VIDEO_RETRIES;

                if (shouldRetry) {
                    System.out.println("[UI] Retrying video playback in 2 seconds... (Attempt " + (retryCount + 1) + "/" + MAX_VIDEO_RETRIES + ")");
                    if (nowPlayingLabel != null) {
                        nowPlayingLabel.setText("Retrying... (" + (retryCount + 1) + "/" + MAX_VIDEO_RETRIES + ")");
                        nowPlayingLabel.setStyle("-fx-text-fill: #f39c12;");
                    }
                    
                    // Retry sau 2 giây
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000);
                            javafx.application.Platform.runLater(() -> {
                                playVideoStream(streamUrl, fileName, retryCount + 1);
                            });
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                } else {
                    // Không thể retry nữa hoặc lỗi nghiêm trọng
                    if (statusLabel != null) {
                        if (retryCount >= MAX_VIDEO_RETRIES) {
                            statusLabel.setText("⚠️ Failed to load video after " + MAX_VIDEO_RETRIES + " attempts. Server may not be ready.");
                        } else if (err.contains("ERROR_MEDIA_INVALID") || err.contains("UNKNOWN")) {
                            statusLabel.setText("⚠️ Loading Video... Please wait for pieces to download.");
                        } else {
                            statusLabel.setText("Media Error: " + (currentMediaPlayer.getError() != null ? currentMediaPlayer.getError().getMessage() : err));
                        }
                    }
                    if (nowPlayingLabel != null) {
                        if (retryCount >= MAX_VIDEO_RETRIES) {
                            nowPlayingLabel.setText("Playback failed: " + fileName);
                            nowPlayingLabel.setStyle("-fx-text-fill: #e74c3c;");
                        } else {
                            nowPlayingLabel.setText("Buffering... (Please wait)");
                            nowPlayingLabel.setStyle("-fx-text-fill: #f39c12;");
                        }
                    }
                }
            });

            // Auto Play
            currentMediaPlayer.setAutoPlay(true);

        } catch (Exception e) {
            System.err.println("Error initializing player: " + e.getMessage());
            if (statusLabel != null) {
                if (retryCount < MAX_VIDEO_RETRIES) {
                    statusLabel.setText("Retrying player initialization...");
                    // Retry sau 1 giây
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            javafx.application.Platform.runLater(() -> {
                                playVideoStream(streamUrl, fileName, retryCount + 1);
                            });
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                } else {
                    statusLabel.setText("Player Error: Please ensure you have codecs installed.");
                }
            }
        }
    }

    // =================================================================================
    // SETUP METHODS
    // =================================================================================

    private void setupFileListView() {
        if (fileListView == null)
            return;

        fileListView.setCellFactory(listView -> new javafx.scene.control.ListCell<FileInfo>() {
            @Override
            protected void updateItem(FileInfo fileInfo, boolean empty) {
                super.updateItem(fileInfo, empty);

                if (empty || fileInfo == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    // Create Card UI
                    VBox root = new VBox(5);
                    root.setStyle(
                            "-fx-padding: 10; -fx-background-color: white; -fx-background-radius: 5; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 0, 1);");

                    Label name = new Label(fileInfo.getFileName());
                    name.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

                    String type = fileInfo.isHasManifest() ? "Hybrid P2P" : "Direct HTTP";
                    Label meta = new Label(fileInfo.getFormattedSize() + " • " + type);
                    meta.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 11px;");

                    Button downloadBtn = new Button("Download");
                    downloadBtn.setStyle(
                            "-fx-background-color: #3498db; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");
                    downloadBtn.setOnAction(e -> startDownloadFromFileInfo(fileInfo));

                    HBox top = new HBox(10, name);
                    HBox bottom = new HBox(10, meta, new Region(), downloadBtn);
                    HBox.setHgrow(new Region(), Priority.ALWAYS); // Spacer

                    root.getChildren().addAll(top, bottom);
                    setGraphic(root);
                    setText(null);
                }
            }
        });
    }

    private void setupSearchAndFilter() {
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> applySearchAndFilter());
        }
    }

    @FXML
    private void onFilterCategory(javafx.event.ActionEvent event) {
        if (event.getSource() instanceof javafx.scene.control.Button) {
            javafx.scene.control.Button btn = (javafx.scene.control.Button) event.getSource();
            // Reset styles
            if (filterAllButton != null)
                filterAllButton.getStyleClass().remove("filter-chip-selected");
            if (filterVideoButton != null)
                filterVideoButton.getStyleClass().remove("filter-chip-selected");
            if (filterArchiveButton != null)
                filterArchiveButton.getStyleClass().remove("filter-chip-selected");

            // Set active
            btn.getStyleClass().add("filter-chip-selected");

            // Apply filter
            if (btn == filterAllButton)
                selectedCategory = "All";
            else if (btn == filterVideoButton)
                selectedCategory = "Video";
            else if (btn == filterArchiveButton)
                selectedCategory = "Archive";
            else
                selectedCategory = btn.getText(); // Fallback

            applySearchAndFilter();
        }
    }

    private void applySearchAndFilter() {
        if (allFileInfos == null)
            return;

        String search = (searchField != null) ? searchField.getText().toLowerCase().trim() : "";

        List<FileInfo> filtered = allFileInfos.stream()
                .filter(f -> {
                    // Search match
                    if (!search.isEmpty() && !f.getFileName().toLowerCase().contains(search))
                        return false;
                    // Category match
                    String cats = detectCategory(f.getFileName());
                    if (!selectedCategory.equals("All") && !cats.equals(selectedCategory)) {
                        // Slight loose matching for Archives/Video
                        if (selectedCategory.equals("Archives") && cats.equals("Archive"))
                            return true;
                        return false;
                    }
                    return true;
                })
                .collect(java.util.stream.Collectors.toList());

        if (fileListView != null) {
            fileListView.setItems(FXCollections.observableArrayList(filtered));
        }

        if (fileCountLabel != null) {
            fileCountLabel.setText("(" + filtered.size() + ")");
        }
    }

    private String detectCategory(String fileName) {
        if (fileName == null)
            return "File";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith("mov") || lower.endsWith("avi"))
            return "Video";
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z"))
            return "Archive";
        return "Other";
    }

    // =================================================================================
    // DOWNLOAD LOGIC
    // =================================================================================

    private void startDownloadFromFileInfo(FileInfo fileInfo) {
        if (fileInfo == null)
            return;

        String fileName = fileInfo.getFileName();
        String originUrl = mainApp.getOriginServerUrl();
        String manifestUrl = originUrl + "/manifest/" + fileName;

        if (statusLabel != null)
            statusLabel.setText("Starting download: " + fileName);

        if (!fileInfo.isHasManifest()) {
            showError("File " + fileName + " does not have a manifest. Cannot download.");
            return;
        }

        // Start Process
        mainApp.startDownload(manifestUrl);

        if (statusLabel != null)
            statusLabel.setText("Added: " + fileName + " to download queue");

        // Optimistic UI: If video, show loading state on player
        if (detectCategory(fileName).equals("Video")) {
            if (playerPlaceholder != null)
                playerPlaceholder.setVisible(true);
            if (nowPlayingLabel != null)
                nowPlayingLabel.setText("Waiting for stream... (" + fileName + ")");
        }
    }

    @FXML
    private void onRefreshFiles() {
        if (statusLabel != null)
            statusLabel.setText("Refreshing file list...");
        fetchAvailableFiles();
    }

    // Legacy support handlers
    @FXML
    private void onAddDownload() {
    }

    @FXML
    private void onSearchTextChanged() {
        applySearchAndFilter();
    }

    private void fetchAvailableFiles() {
        new Thread(() -> {
            try {
                // Try modern API first
                String infoUrl = mainApp.getOriginServerUrl() + "/files/info";
                Request request = new Request.Builder().url(infoUrl).build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        List<FileInfo> fileInfos = gson.fromJson(json, new TypeToken<List<FileInfo>>() {
                        }.getType());
                        for (FileInfo f : fileInfos)
                            f.initializeFromDeserialized();

                        javafx.application.Platform.runLater(() -> {
                            allFileInfos = FXCollections.observableArrayList(fileInfos);
                            applySearchAndFilter();
                            if (statusLabel != null)
                                statusLabel.setText("File list updated.");
                        });
                        return;
                    }
                }
            } catch (Exception e) {
                // Fallback to legacy
                fetchLegacyFiles();
            }
        }).start();
    }

    private void fetchLegacyFiles() {
        try {
            String listUrl = mainApp.getOriginServerUrl() + "/files/list";
            Request request = new Request.Builder().url(listUrl).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    List<String> files = gson.fromJson(response.body().string(), new TypeToken<List<String>>() {
                    }.getType());
                    List<FileInfo> fileInfos = new java.util.ArrayList<>();
                    for (String f : files)
                        fileInfos.add(new FileInfo(f, 0, null, 0, true));
                    javafx.application.Platform.runLater(() -> {
                        allFileInfos = FXCollections.observableArrayList(fileInfos);
                        applySearchAndFilter();
                    });
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void updateProgress() {
        if (downloadTasks != null) {
            downloadTasks.forEach(DownloadTask::updateProgress);
        }
    }

    private void setupDragAndDrop() {
        // Feature disabled as download list UI is removed
    }

    public void showError(String errorMessage) {
        if (statusLabel != null) {
            statusLabel.setText("❌ " + errorMessage);
        }
        System.err.println("[UI Error] " + errorMessage);
    }

    // Setters
    // Setters
    public void setMainApp(MainApp mainApp) {
        this.mainApp = mainApp;
        // Safe to fetch files now that mainApp is set
        fetchAvailableFiles();
    }

    public void setDownloadTasks(ObservableList<DownloadTask> downloadTasks) {
        this.downloadTasks = downloadTasks;
        // download list UI removed
        setupDragAndDrop();
    }

    /**
     * Updates the progress bar under the video player.
     * Called by MainApp during streaming/download.
     */
    public void updateVideoProgress(DownloadTask task) {
        if (videoProgressBar == null || task == null)
            return;

        // Bind progress properties
        videoProgressBar.progressProperty().bind(task.progressProperty());

        // Use a listener to update text labels (cleaner than complex binding)
        task.progressProperty().addListener((obs, oldVal, newVal) -> {
            double p = newVal.doubleValue() * 100;
            javafx.application.Platform.runLater(() -> {
                if (videoProgressLabel != null)
                    videoProgressLabel.setText(String.format("%.1f%%", p));
            });
        });

        // Speed (mock or if available in task)
        task.detailsProperty().addListener((obs, oldVal, newVal) -> {
            javafx.application.Platform.runLater(() -> {
                if (videoSpeedLabel != null)
                    videoSpeedLabel.setText(newVal); // Usually contains speed/size info
            });
        });

        // Show the container
        if (downloadProgressContainer != null)
            downloadProgressContainer.setVisible(true);
    }

    public void hideVideoProgress() {
        if (downloadProgressContainer != null)
            downloadProgressContainer.setVisible(false);
    }
}
