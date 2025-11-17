package com.fragmented.download.javafx.controller;

import com.fragmented.download.javafx.model.DownloadTask;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

/**
 * Controller for a single download item in the list (DownloadCell.fxml).
 * Binds UI elements to the properties of a DownloadTask.
 */
public class DownloadCellController {

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

    private DownloadTask downloadTask;

    public void setDownloadTask(DownloadTask task) {
        this.downloadTask = task;
        bindToTask();
    }

    private void bindToTask() {
        if (downloadTask == null) {
            return;
        }
        // Bind properties
        fileNameLabel.textProperty().bind(downloadTask.fileNameProperty());
        progressBar.progressProperty().bind(downloadTask.progressProperty());
        statusLabel.textProperty().bind(downloadTask.statusProperty());

        // Bind button states
        pauseButton.disableProperty().bind(downloadTask.canPauseProperty().not());
        resumeButton.disableProperty().bind(downloadTask.canResumeProperty().not());
    }

    @FXML
    private void handlePause() {
        if (downloadTask != null) {
            downloadTask.getScheduler().pause();
        }
    }

    @FXML
    private void handleResume() {
        if (downloadTask != null) {
            downloadTask.getScheduler().start();
        }
    }
}