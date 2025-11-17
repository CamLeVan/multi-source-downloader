package com.fragmented.download.javafx.controller;

import com.fragmented.download.javafx.model.DownloadTask;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ListCell;

import java.io.IOException;

/**
 * Custom ListCell to display a DownloadTask.
 * It loads the DownloadCell.fxml layout for each item in the ListView.
 */
public class DownloadListCell extends ListCell<DownloadTask> {

    private Parent graphic;
    private DownloadCellController controller;

    public DownloadListCell() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/DownloadCell.fxml"));
            graphic = loader.load();
            controller = loader.getController();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void updateItem(DownloadTask task, boolean empty) {
        super.updateItem(task, empty);
        if (empty || task == null) {
            setText(null);
            setGraphic(null);
        } else {
            controller.setDownloadTask(task);
            setGraphic(graphic);
        }
    }
}