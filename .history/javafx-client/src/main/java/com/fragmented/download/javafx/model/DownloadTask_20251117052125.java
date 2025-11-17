package com.fragmented.download.javafx.model;

import com.fragmented.download.javafx.logic.Scheduler;
import javafx.beans.property.*;

/**
 * Represents a single download task in the UI.
 * This class is a JavaFX Bean that wraps a Scheduler and exposes its state
 * through JavaFX properties, allowing the UI to bind to it.
 */
public class DownloadTask {

    private final StringProperty fileName;
    private final DoubleProperty progress;
    private final StringProperty status;
    private final ReadOnlyBooleanWrapper canPause;
    private final ReadOnlyBooleanWrapper canResume;
    private final Scheduler scheduler;

    public DownloadTask(String fileId, Scheduler scheduler) {
        this.fileName = new SimpleStringProperty(fileId);
        this.scheduler = scheduler;

        // Initialize properties
        this.progress = new SimpleDoubleProperty(0);
        this.status = new SimpleStringProperty("Queued");
        this.canPause = new ReadOnlyBooleanWrapper(false);
        this.canResume = new ReadOnlyBooleanWrapper(false);

        // Bind the task's UI properties to the underlying scheduler's properties
        this.progress.bind(scheduler.progressProperty());
        this.status.bind(scheduler.statusMessageProperty());

        // Listen for changes in the scheduler's state to update button usability
        scheduler.stateProperty().addListener((obs, oldState, newState) -> updateButtonStates(newState));
        
        // Set initial button states
        updateButtonStates(scheduler.getState());
    }

    private void updateButtonStates(Scheduler.State state) {
        canPause.set(state == Scheduler.State.RUNNING);
        canResume.set(state == Scheduler.State.PAUSED);
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    // --- JavaFX Property Getters ---

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

    public ReadOnlyBooleanProperty canPauseProperty() { return canPause.getReadOnlyProperty(); }

    public ReadOnlyBooleanProperty canResumeProperty() { return canResume.getReadOnlyProperty(); }
}