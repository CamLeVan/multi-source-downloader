package com.fragmented.download.javafx.controller;

import com.fragmented.download.javafx.MainApp;
import com.fragmented.download.javafx.model.DownloadTask;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.util.Duration;

public class DashboardController {

    @FXML
    private ListView<DownloadTask> downloadListView;

    @FXML
    private Label dropLabel;

    private ObservableList<DownloadTask> downloadTasks;
    private MainApp mainApp;

    public void initialize() {
        // Set up a timeline to update the progress bars periodically.
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(0), event -> updateProgress()),
                new KeyFrame(Duration.seconds(1))
        );
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

        setupLinkDrop();
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

    private void updateProgress() {
        if (downloadTasks != null) {
            downloadTasks.forEach(DownloadTask::updateProgress);
        }
    }

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

    private void setupLinkDrop() {
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
                mainApp.startDownload(db.getUrl());
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }
}
