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
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.util.Duration;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.List;

/**
 * DashboardController với UI hoàn chỉnh
 * Hỗ trợ: chọn file, nhập custom file, fetch file list từ server
 */
public class DashboardController {

    @FXML
    private ListView<DownloadTask> downloadListView;

    @FXML
    private ComboBox<String> fileComboBox; // Legacy - giữ lại để không break FXML

    @FXML
    @SuppressWarnings("unchecked")
    private ListView<FileInfo> fileListView; // Phase 1.7: File list với card layout (generic type set in code)

    @FXML
    private TextField fileNameField;

    @FXML
    private Button addDownloadButton;

    @FXML
    private Button refreshButton;

    @FXML
    private Label statusLabel;

    // Phase 2: Search and Filter components
    @FXML
    private TextField searchField;
    
    @FXML
    private ComboBox<String> sortComboBox;
    
    @FXML
    private Label fileCountLabel;
    
    // Category Filter buttons
    @FXML
    private Button filterAllButton;
    
    @FXML
    private Button filterArchiveButton;
    
    @FXML
    private Button filterApplicationButton;
    
    @FXML
    private Button filterDocumentButton;
    
    @FXML
    private Button filterVideoButton;
    
    @FXML
    private Button filterImageButton;
    
    // Current selected category filter
    private String selectedCategory = "All";

    private ObservableList<DownloadTask> downloadTasks;
    private MainApp mainApp;
    private final OkHttpClient httpClient = new OkHttpClient();
    // Gson với excludeFieldsWithoutExposeAnnotation để chỉ deserialize fields có @Expose
    private final Gson gson = new GsonBuilder()
        .excludeFieldsWithoutExposeAnnotation()
        .create();
    
    // Phase 1.6: Store fileInfos để hiển thị metadata
    private java.util.Map<String, FileInfo> fileInfoMap = new java.util.HashMap<>();
    
    // Phase 2: Store original file list để filter
    private ObservableList<FileInfo> allFileInfos = FXCollections.observableArrayList();

    public void initialize() {
        // Set up a timeline to update the progress bars periodically.
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(0), event -> updateProgress()),
                new KeyFrame(Duration.seconds(1))
        );
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

        // Phase 1.7: Setup ListView với FileCard cells
        setupFileListView();

        // Phase 2: Setup Search and Filter
        setupSearchAndFilter();
        
        // Phase 2: Setup Category Filters
        if (filterAllButton != null) {
            filterAllButton.getStyleClass().add("category-filter-active");
        }
        
        // Phase 2: Initialize sort ComboBox items
        if (sortComboBox != null) {
            sortComboBox.setItems(FXCollections.observableArrayList(
                "Name (A-Z)",
                "Name (Z-A)",
                "Size (Small to Large)",
                "Size (Large to Small)",
                "Date (Newest First)",
                "Date (Oldest First)"
            ));
        }

        // Phase 1.6: Setup ComboBox listener (legacy - có thể remove sau)
        if (fileComboBox != null) {
            setupFileComboBoxListener();
        }

        // Fetch available files khi khởi động
        fetchAvailableFiles();
    }
    
    /**
     * Phase 1.6: Setup listener cho ComboBox để hiển thị metadata
     */
    private void setupFileComboBoxListener() {
        fileComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && fileInfoMap.containsKey(newVal)) {
                FileInfo fileInfo = fileInfoMap.get(newVal);
                // Hiển thị metadata trong status label
                String metadata = String.format("Size: %s | Modified: %s | Mirrors: %d | Manifest: %s",
                    fileInfo.getFormattedSize(),
                    fileInfo.getFormattedDate(),
                    fileInfo.getMirrorsCount(),
                    fileInfo.isHasManifest() ? "Yes" : "No"
                );
                statusLabel.setText(metadata);
            }
        });
    }
    
    /**
     * Phase 1.7: Setup ListView với FileCard cells
     */
    private void setupFileListView() {
        fileListView.setCellFactory(listView -> new javafx.scene.control.ListCell<FileInfo>() {
            @Override
            protected void updateItem(FileInfo fileInfo, boolean empty) {
                super.updateItem(fileInfo, empty);
                
                if (empty || fileInfo == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    try {
                        // Load FileCard FXML
                        FXMLLoader loader = new FXMLLoader(
                            getClass().getResource("/fxml/FileCard.fxml")
                        );
                        HBox cardRoot = loader.load();
                        FileCardController controller = loader.getController();
                        
                        // Set file info và callback
                        controller.setFileInfo(fileInfo, () -> {
                            // Callback khi click Download button trên card
                            startDownloadFromFileInfo(fileInfo);
                        });
                        
                        setGraphic(cardRoot);
                        setText(null);
                    } catch (IOException e) {
                        System.err.println("Error loading FileCard: " + e.getMessage());
                        e.printStackTrace();
                        // Fallback: hiển thị text đơn giản
                        setText(fileInfo.getFileName() + " (" + fileInfo.getFormattedSize() + ")");
                        setGraphic(null);
                    }
                }
            }
        });
    }
    
    /**
     * Phase 2: Setup Search and Filter listeners
     */
    private void setupSearchAndFilter() {
        // Search field listener
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> {
                applySearchAndFilter();
            });
        }
        
        // Sort combo box listener
        if (sortComboBox != null) {
            sortComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
                applySearchAndFilter();
            });
        }
    }
    
    /**
     * Phase 2: Apply search and filter to file list
     */
    private void applySearchAndFilter() {
        if (allFileInfos == null || allFileInfos.isEmpty()) {
            return;
        }
        
        // Get search text
        String searchText = searchField != null ? searchField.getText().toLowerCase().trim() : "";
        
        // Filter by search text and category
        java.util.List<FileInfo> filtered = allFileInfos.stream()
            .filter(fileInfo -> {
                // Search filter
                if (!searchText.isEmpty()) {
                    String fileName = fileInfo.getFileName().toLowerCase();
                    if (!fileName.contains(searchText)) {
                        return false;
                    }
                }
                
                // Category filter
                if (selectedCategory != null && !selectedCategory.equals("All")) {
                    String category = detectCategory(fileInfo.getFileName());
                    if (!category.equals(selectedCategory)) {
                        return false;
                    }
                }
                
                return true;
            })
            .collect(java.util.stream.Collectors.toList());
        
        // Sort
        String sortOption = sortComboBox != null ? sortComboBox.getValue() : null;
        if (sortOption != null) {
            switch (sortOption) {
                case "Name (A-Z)":
                    filtered.sort((a, b) -> a.getFileName().compareToIgnoreCase(b.getFileName()));
                    break;
                case "Name (Z-A)":
                    filtered.sort((a, b) -> b.getFileName().compareToIgnoreCase(a.getFileName()));
                    break;
                case "Size (Small to Large)":
                    filtered.sort((a, b) -> Long.compare(a.getSize(), b.getSize()));
                    break;
                case "Size (Large to Small)":
                    filtered.sort((a, b) -> Long.compare(b.getSize(), a.getSize()));
                    break;
                case "Date (Newest First)":
                    filtered.sort((a, b) -> {
                        if (a.getLastModified() == null || b.getLastModified() == null) {
                            return 0;
                        }
                        return b.getLastModified().compareTo(a.getLastModified());
                    });
                    break;
                case "Date (Oldest First)":
                    filtered.sort((a, b) -> {
                        if (a.getLastModified() == null || b.getLastModified() == null) {
                            return 0;
                        }
                        return a.getLastModified().compareTo(b.getLastModified());
                    });
                    break;
            }
        }
        
        // Update ListView
        fileListView.setItems(FXCollections.observableArrayList(filtered));
        
        // Update file count label
        if (fileCountLabel != null) {
            int total = allFileInfos.size();
            int showing = filtered.size();
            if (showing == total) {
                fileCountLabel.setText("(" + total + " files)");
            } else {
                fileCountLabel.setText("(" + showing + " of " + total + " files)");
            }
        }
    }
    
    /**
     * Phase 2: Handler cho search field key release (FXML binding)
     */
    @FXML
    private void onSearchTextChanged() {
        applySearchAndFilter();
    }
    
    /**
     * Phase 2: Handler cho category filter buttons
     */
    @FXML
    private void onFilterCategory(javafx.event.ActionEvent event) {
        if (event.getSource() instanceof javafx.scene.control.Button) {
            javafx.scene.control.Button btn = (javafx.scene.control.Button) event.getSource();
            selectedCategory = btn.getText();
            
            // Update button styles
            updateCategoryFilterButtons();
            
            // Apply filter
            applySearchAndFilter();
        }
    }
    
    /**
     * Helper method để detect category từ file name
     */
    private String detectCategory(String fileName) {
        if (fileName == null) return "File";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") ||
            lower.endsWith(".tar") || lower.endsWith(".gz")) {
            return "Archive";
        } else if (lower.endsWith(".exe") || lower.endsWith(".msi") || lower.endsWith(".app")) {
            return "Application";
        } else if (lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return "Document";
        } else if (lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv") ||
                   lower.endsWith(".mov") || lower.endsWith(".wmv")) {
            return "Video";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".gif") ||
                   lower.endsWith(".jpeg") || lower.endsWith(".bmp")) {
            return "Image";
        }
        return "File";
    }
    
    /**
     * Update category filter button styles
     */
    private void updateCategoryFilterButtons() {
        if (filterAllButton != null) {
            filterAllButton.getStyleClass().removeAll("category-filter-active");
            if (selectedCategory.equals("All")) {
                filterAllButton.getStyleClass().add("category-filter-active");
            }
        }
        if (filterArchiveButton != null) {
            filterArchiveButton.getStyleClass().removeAll("category-filter-active");
            if (selectedCategory.equals("Archive")) {
                filterArchiveButton.getStyleClass().add("category-filter-active");
            }
        }
        if (filterApplicationButton != null) {
            filterApplicationButton.getStyleClass().removeAll("category-filter-active");
            if (selectedCategory.equals("Application")) {
                filterApplicationButton.getStyleClass().add("category-filter-active");
            }
        }
        if (filterDocumentButton != null) {
            filterDocumentButton.getStyleClass().removeAll("category-filter-active");
            if (selectedCategory.equals("Document")) {
                filterDocumentButton.getStyleClass().add("category-filter-active");
            }
        }
        if (filterVideoButton != null) {
            filterVideoButton.getStyleClass().removeAll("category-filter-active");
            if (selectedCategory.equals("Video")) {
                filterVideoButton.getStyleClass().add("category-filter-active");
            }
        }
        if (filterImageButton != null) {
            filterImageButton.getStyleClass().removeAll("category-filter-active");
            if (selectedCategory.equals("Image")) {
                filterImageButton.getStyleClass().add("category-filter-active");
            }
        }
    }

    /**
     * Phase 1.7: Start download từ FileInfo (giữ nguyên logic)
     */
    private void startDownloadFromFileInfo(FileInfo fileInfo) {
        if (fileInfo == null) return;
        
        String fileName = fileInfo.getFileName();
        String originUrl = mainApp.getOriginServerUrl();
        String manifestUrl = originUrl + "/manifest/" + fileName;
        
        statusLabel.setText("Starting download: " + fileName);
        
        // Check if file has manifest before starting download
        if (!fileInfo.isHasManifest()) {
            showError("File " + fileName + " does not have a manifest. Cannot download.");
            return;
        }
        
        mainApp.startDownload(manifestUrl); // Giữ nguyên logic download
        statusLabel.setText("Added: " + fileName + " to download queue");
    }
    
    /**
     * Hiển thị error message cho user
     */
    public void showError(String errorMessage) {
        if (statusLabel != null) {
            statusLabel.setText("❌ Error: " + errorMessage);
            statusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
            
            // Reset style sau 5 giây
            javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(5), e -> {
                    statusLabel.setStyle("");
                })
            );
            timeline.play();
        }
        System.err.println("[UI Error] " + errorMessage);
    }

    /**
     * Phase 1.6: Setup custom cell factory để hiển thị metadata trong ComboBox dropdown (Legacy)
     */
    private void setupComboBoxCellFactory() {
        if (fileComboBox == null) return;
        fileComboBox.setCellFactory(listView -> new javafx.scene.control.ListCell<String>() {
            @Override
            protected void updateItem(String fileName, boolean empty) {
                super.updateItem(fileName, empty);
                if (empty || fileName == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Hiển thị tên file
                    setText(fileName);
                    
                    // Nếu có metadata, hiển thị thêm thông tin
                    if (fileInfoMap.containsKey(fileName)) {
                        FileInfo fileInfo = fileInfoMap.get(fileName);
                        // Tooltip với metadata đầy đủ
                        Tooltip tooltip = new Tooltip(
                            String.format("File: %s\nSize: %s\nModified: %s\nMirrors: %d\nHas Manifest: %s",
                                fileName,
                                fileInfo.getFormattedSize(),
                                fileInfo.getFormattedDate(),
                                fileInfo.getMirrorsCount(),
                                fileInfo.isHasManifest() ? "Yes" : "No"
                            )
                        );
                        setTooltip(tooltip);
                    }
                }
            }
        });
        
        // Cũng setup cho button cell (khi ComboBox đóng)
        fileComboBox.setButtonCell(new javafx.scene.control.ListCell<String>() {
            @Override
            protected void updateItem(String fileName, boolean empty) {
                super.updateItem(fileName, empty);
                if (empty || fileName == null) {
                    setText(null);
                } else {
                    setText(fileName);
                }
            }
        });
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
     * Fetch danh sách files có sẵn từ Origin Server (Legacy - giữ lại để backward compatibility)
     */
    private void fetchAvailableFiles() {
        // Phase 1.5: Thử fetch metadata trước, nếu fail thì fallback về legacy API
        fetchFilesWithMetadata();
    }

    /**
     * Phase 1.5: Fetch files với metadata (size, date, mirrors)
     * Nếu API mới không available, fallback về legacy API
     */
    private void fetchFilesWithMetadata() {
        new Thread(() -> {
            try {
                String originUrl = mainApp.getOriginServerUrl();
                String infoUrl = originUrl + "/files/info";
                
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Refreshing file list...");
                });
                
                System.out.println("[DEBUG] Fetching files from: " + infoUrl);
                Request request = new Request.Builder().url(infoUrl).build();
                try (Response response = httpClient.newCall(request).execute()) {
                    System.out.println("[DEBUG] Response code: " + response.code());
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        List<FileInfo> fileInfos = gson.fromJson(json, new TypeToken<List<FileInfo>>(){}.getType());
                        
                        // Initialize Properties từ deserialized data
                        for (FileInfo fileInfo : fileInfos) {
                            fileInfo.initializeFromDeserialized();
                        }
                        
                        // Phase 1.6: Store fileInfos vào map để truy cập metadata
                        java.util.Map<String, FileInfo> infoMap = new java.util.HashMap<>();
                        for (FileInfo fileInfo : fileInfos) {
                            infoMap.put(fileInfo.getFileName(), fileInfo);
                        }
                        
                        // Update UI on JavaFX thread
                        javafx.application.Platform.runLater(() -> {
                            // Phase 2: Store original file list
                            allFileInfos = FXCollections.observableArrayList(fileInfos);
                            
                            // Phase 1.6: Store fileInfoMap để hiển thị metadata (legacy support)
                            fileInfoMap = infoMap;
                            
                            // Phase 2: Apply search and filter (sẽ populate ListView)
                            applySearchAndFilter();
                            
                            // Phase 1.6: Setup custom cell factory cho ComboBox (legacy)
                            if (fileComboBox != null) {
                                fileComboBox.setItems(FXCollections.observableArrayList(
                                    fileInfos.stream()
                                        .map(FileInfo::getFileName)
                                        .collect(java.util.stream.Collectors.toList())
                                ));
                                setupComboBoxCellFactory();
                            }
                            
                            statusLabel.setText("Found " + fileInfos.size() + " files on server");
                        });
                        return; // Success, không cần fallback
                    }
                }
            } catch (Exception e) {
                // Fallback to legacy API nếu API mới fail
                System.err.println("[ERROR] Metadata API failed: " + e.getMessage());
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Error: Could not fetch file list - " + e.getMessage());
                });
            }
            
            // Fallback: Fetch legacy API (chỉ tên files)
            fetchAvailableFilesLegacy();
        }).start();
    }

    /**
     * Legacy method: Fetch chỉ tên files (backward compatibility)
     * Dùng khi Origin Server chưa có API /files/info
     */
    private void fetchAvailableFilesLegacy() {
        new Thread(() -> {
            try {
                String originUrl = mainApp.getOriginServerUrl();
                String listUrl = originUrl + "/files/list";
                
                System.out.println("[DEBUG] Fallback: Fetching from legacy API: " + listUrl);
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Fetching files (legacy mode)...");
                });
                
                Request request = new Request.Builder().url(listUrl).build();
                try (Response response = httpClient.newCall(request).execute()) {
                    System.out.println("[DEBUG] Legacy API response code: " + response.code());
                    if (response.isSuccessful() && response.body() != null) {
                        String json = response.body().string();
                        List<String> files = gson.fromJson(json, new TypeToken<List<String>>(){}.getType());
                        
                        System.out.println("[DEBUG] Legacy API returned " + files.size() + " files");
                        
                        // Convert String list to FileInfo list (với metadata mặc định)
                        List<FileInfo> fileInfos = new java.util.ArrayList<>();
                        for (String fileName : files) {
                            FileInfo fileInfo = new FileInfo(fileName, 0, null, 0, false);
                            fileInfos.add(fileInfo);
                        }
                        
                        // Update UI on JavaFX thread
                        javafx.application.Platform.runLater(() -> {
                            // Store original file list
                            allFileInfos = FXCollections.observableArrayList(fileInfos);
                            
                            // Apply search and filter (sẽ populate ListView)
                            applySearchAndFilter();
                            
                            // Update ComboBox (legacy)
                            if (fileComboBox != null) {
                                fileComboBox.setItems(FXCollections.observableArrayList(files));
                                setupComboBoxCellFactory();
                            }
                            
                            statusLabel.setText("Found " + files.size() + " files on server (legacy mode - no metadata)");
                        });
                    }
                }
            } catch (Exception e) {
                System.err.println("[ERROR] Legacy API also failed: " + e.getMessage());
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    String errorMsg = "Error: Could not fetch file list. ";
                    if (e.getMessage() != null) {
                        if (e.getMessage().contains("Connection refused") || e.getMessage().contains("connect")) {
                            errorMsg += "Origin Server may not be running. Please start Origin Server on Windows.";
                        } else {
                            errorMsg += e.getMessage();
                        }
                    } else {
                        errorMsg += "Please check if Origin Server is running on " + mainApp.getOriginServerUrl();
                    }
                    statusLabel.setText(errorMsg);
                });
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
        if (fileNameField != null && fileNameField.getText() != null && !fileNameField.getText().trim().isEmpty()) {
            fileName = fileNameField.getText().trim();
        }
        // Nếu không có, lấy từ ComboBox (legacy - có thể null nếu FXML không có)
        else if (fileComboBox != null && fileComboBox.getValue() != null) {
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
