package com.fragmented.download.javafx.controller;

import com.fragmented.download.javafx.MainApp;
import com.fragmented.download.javafx.model.DownloadTask;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.util.Callback;

/**
 * Controller for the main dashboard view (Dashboard.fxml).
 * Handles UI logic for displaying download tasks and adding new ones via drag-and-drop.
 */
public class DashboardController {

    @FXML
    private ListView<DownloadTask> downloadListView;

    @FXML
    private Label dropLabel;

    private MainApp mainApp;
    private ObservableList<DownloadTask> downloadTasks;

    /**
     * Initializes the controller class. This method is automatically called
     * after the fxml file has been loaded.
     */
    @FXML
    private void initialize() {
        // Set up the custom cell factory for the ListView
        downloadListView.setCellFactory(new Callback<>() {
            @Override
            public ListCell<DownloadTask> call(ListView<DownloadTask> listView) {
                return new DownloadListCell();
            }
        });

        // Set up drag-and-drop handlers for the label
        setupDragAndDrop();
    }

    public void setMainApp(MainApp mainApp) {
        this.mainApp = mainApp;
    }

    public void setDownloadTasks(ObservableList<DownloadTask> downloadTasks) {
        this.downloadTasks = downloadTasks;
        downloadListView.setItems(this.downloadTasks);
    }

    private void setupDragAndDrop() {
        dropLabel.setOnDragOver(event -> {
            if (event.getGestureSource() != dropLabel && event.getDragboard().hasUrl()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        dropLabel.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasUrl()) {
                String url = db.getUrl();
                System.out.println("Dropped URL: " + url);
                // Call MainApp to start the download
                if (mainApp != null) {
                    mainApp.startDownload(url);
                }
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        dropLabel.setOnDragEntered(event -> {
            if (event.getGestureSource() != dropLabel && event.getDragboard().hasUrl()) {
                dropLabel.setStyle("-fx-border-color: #00aaff; -fx-border-width: 2; -fx-border-style: dashed; -fx-padding: 20px;");
            }
        });

        dropLabel.setOnDragExited(event -> {
            dropLabel.setStyle("-fx-border-color: #c6c6c6; -fx-border-width: 2; -fx-border-style: dashed; -fx-padding: 20px;");
        });
    }
}