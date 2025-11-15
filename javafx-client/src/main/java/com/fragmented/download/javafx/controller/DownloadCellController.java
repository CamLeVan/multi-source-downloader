package com.fragmented.download.javafx.controller;

import com.fragmented.download.javafx.model.DownloadTask;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.fxml.FXMLLoader;

import java.io.IOException;

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

    private FXMLLoader fxmlLoader;

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
                }
            }

            fileNameLabel.textProperty().bind(task.fileNameProperty());
            progressBar.progressProperty().bind(task.progressProperty());
            statusLabel.textProperty().bind(task.statusProperty());

            setGraphic(hBox);
        }
    }

    @FXML
    private void handlePause() {
        getItem().getScheduler().pause();
        pauseButton.setDisable(true);
        resumeButton.setDisable(false);
        getItem().statusProperty().set("Paused");
    }

    @FXML
    private void handleResume() {
        getItem().getScheduler().resume();
        pauseButton.setDisable(false);
        resumeButton.setDisable(true);
        getItem().statusProperty().set("Downloading");
    }
}
