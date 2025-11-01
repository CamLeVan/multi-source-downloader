package com.fragmented.download.javafx.controller;

import com.fragmented.download.javafx.logic.Scheduler;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;
import javafx.util.Duration;

public class DashboardController {

    @FXML
    private ProgressBar overallProgressBar;

    private Scheduler scheduler;

    public void initialize() {
        // Set up a timeline to update the progress bar periodically.
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(0), event -> updateProgress()),
                new KeyFrame(Duration.seconds(1))
        );
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    private void updateProgress() {
        if (scheduler != null) {
            double progress = scheduler.getProgress();
            // Ensure UI updates are done on the JavaFX Application Thread.
            Platform.runLater(() -> overallProgressBar.setProgress(progress));
        }
    }
}
