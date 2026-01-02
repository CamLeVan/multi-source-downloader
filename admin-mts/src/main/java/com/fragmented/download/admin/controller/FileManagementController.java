package com.fragmented.download.admin.controller;

import com.fragmented.download.admin.model.FileInfo;
import com.fragmented.download.admin.service.OriginServerService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * File Management Controller
 * Handles file operations: list, upload, delete, regenerate manifest
 */
public class FileManagementController {

    @FXML
    private TableView<FileInfo> filesTable;
    @FXML
    private TableColumn<FileInfo, String> fileNameColumn;
    @FXML
    private TableColumn<FileInfo, String> sizeColumn;
    @FXML
    private TableColumn<FileInfo, String> lastModifiedColumn;
    @FXML
    private TableColumn<FileInfo, Boolean> manifestColumn;
    @FXML
    private TableColumn<FileInfo, Void> actionsColumn;
    @FXML
    private Label statusLabel;

    private final OriginServerService originService = new OriginServerService();
    private final ObservableList<FileInfo> files = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        setupTable();
        refreshFiles();
    }

    private void setupTable() {
        fileNameColumn.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("sizeFormatted"));
        lastModifiedColumn.setCellFactory(column -> new TableCell<FileInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    FileInfo file = getTableRow().getItem();
                    if (file.getLastModified() != null) {
                        setText(file.getLastModified().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                    } else {
                        setText("Unknown");
                    }
                }
            }
        });
        manifestColumn.setCellValueFactory(new PropertyValueFactory<>("hasManifest"));
        manifestColumn.setCellFactory(column -> new TableCell<FileInfo, Boolean>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setText(null);
                } else {
                    setText(getTableRow().getItem().isHasManifest() ? "✓ Yes" : "✗ No");
                }
            }
        });

        actionsColumn.setCellFactory(column -> new TableCell<FileInfo, Void>() {
            private final Button deleteBtn = new Button("Delete");
            private final Button regenerateBtn = new Button("Regenerate Manifest");

            {
                deleteBtn.setOnAction(e -> {
                    FileInfo file = getTableRow().getItem();
                    if (file != null) {
                        handleDelete(file.getFileName());
                    }
                });
                regenerateBtn.setOnAction(e -> {
                    FileInfo file = getTableRow().getItem();
                    if (file != null) {
                        handleRegenerateManifest(file.getFileName());
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    HBox hbox = new HBox(5, regenerateBtn, deleteBtn);
                    setGraphic(hbox);
                }
            }
        });

        filesTable.setItems(files);
    }

    @FXML
    private void refreshFiles() {
        updateStatus("Loading files...");
        new Thread(() -> {
            try {
                List<FileInfo> fileList = originService.getFiles();
                Platform.runLater(() -> {
                    files.setAll(fileList);
                    updateStatus("Loaded " + fileList.size() + " files");
                });
            } catch (java.net.ConnectException e) {
                Platform.runLater(() -> {
                    updateStatus("Origin Server not running (Port 8080)");
                    files.clear();
                    // Don't show error popup on auto-refresh, only show in status
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    updateStatus("Error: " + e.getMessage());
                    // Only show alert if user manually clicked refresh
                    showAlert(Alert.AlertType.WARNING, "Connection Error", 
                            "Failed to load files", 
                            "Please ensure Origin Server is running on port 8080.\n\n" + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void handleUpload() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Upload");
        File file = fileChooser.showOpenDialog(filesTable.getScene().getWindow());

        if (file != null) {
            updateStatus("Uploading " + file.getName() + "...");
            new Thread(() -> {
                try {
                    originService.uploadFile(file, true);
                    Platform.runLater(() -> {
                        updateStatus("File uploaded successfully");
                        refreshFiles();
                        showAlert(Alert.AlertType.INFORMATION, "Success", "File Uploaded", 
                                "File " + file.getName() + " uploaded successfully");
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        updateStatus("Upload failed: " + e.getMessage());
                        showAlert(Alert.AlertType.ERROR, "Error", "Upload Failed", e.getMessage());
                    });
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private void handleDelete(String fileName) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete File");
        confirm.setContentText("Are you sure you want to delete: " + fileName + "?");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                updateStatus("Deleting " + fileName + "...");
                new Thread(() -> {
                    try {
                        originService.deleteFile(fileName);
                        Platform.runLater(() -> {
                            updateStatus("File deleted successfully");
                            refreshFiles();
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            updateStatus("Delete failed: " + e.getMessage());
                            showAlert(Alert.AlertType.ERROR, "Error", "Delete Failed", e.getMessage());
                        });
                        e.printStackTrace();
                    }
                }).start();
            }
        });
    }

    private void handleRegenerateManifest(String fileName) {
        updateStatus("Regenerating manifest for " + fileName + "...");
        new Thread(() -> {
            try {
                originService.regenerateManifest(fileName);
                Platform.runLater(() -> {
                    updateStatus("Manifest regenerated successfully");
                    refreshFiles();
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Manifest Regenerated", 
                            "Manifest for " + fileName + " regenerated successfully");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    updateStatus("Regenerate failed: " + e.getMessage());
                    showAlert(Alert.AlertType.ERROR, "Error", "Regenerate Failed", e.getMessage());
                });
                e.printStackTrace();
            }
        }).start();
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

