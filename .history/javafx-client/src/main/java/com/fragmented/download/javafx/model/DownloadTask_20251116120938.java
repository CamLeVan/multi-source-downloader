package com.fragmented.download.javafx.model;

import com.fragmented.download.javafx.logic.Scheduler;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Tuần 6: Model đại diện cho một download task trong ListView
 * Sử dụng JavaFX Properties để tự động update UI
 */
public class DownloadTask {

    private final StringProperty fileName = new SimpleStringProperty();
    private final DoubleProperty progress = new SimpleDoubleProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final Scheduler scheduler;

    public DownloadTask(String fileName, Scheduler scheduler) {
        this.fileName.set(fileName);
        this.scheduler = scheduler;
        this.status.set("Downloading");
    }

    // Getters
    public String getFileName() {
        return fileName.get();
    }

    public StringProperty fileNameProperty() {
        return fileName;
    }

    public double getProgress() {
        return progress.get();
    }

    public DoubleProperty progressProperty() {
        return progress;
    }

    public String getStatus() {
        return status.get();
    }

    public StringProperty statusProperty() {
        return status;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * Tuần 6: Update progress từ Scheduler
     * Được gọi từ Timeline trong DashboardController
     */
    public void updateProgress() {
        progress.set(scheduler.getProgress());
    }
}

