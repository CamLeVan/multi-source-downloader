package com.fragmented.download.javafx.controller;

import com.fragmented.download.javafx.logic.Scheduler;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;
import javafx.util.Duration;

public class DashboardController {

    @FXML
    private ProgressBar overallProgressBar;
    
    // Tuần 2: Per-source progress bars
    @FXML
    private ProgressBar originProgressBar;
    
    @FXML
    private ProgressBar mirrorProgressBar;
    
    @FXML
    private ProgressBar peerProgressBar;

    private Scheduler scheduler;

    public void initialize() {
        // Tuần 2: Timeline animate - Update every 100ms for smooth animation
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(100), event -> updateProgress())
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
            
            // Ensure UI updates are done on the JavaFX Application Thread
            Platform.runLater(() -> {
                overallProgressBar.setProgress(progress);
                
                // Tuần 2: Update per-source progress
                // TODO: Scheduler cần expose getOriginProgress(), getMirrorProgress(), getPeerProgress()
                // Tạm thời set về progress tổng (sẽ refactor sau khi Scheduler có per-source tracking)
                originProgressBar.setProgress(progress * 0.4);  // Giả sử Origin handle 40%
                mirrorProgressBar.setProgress(progress * 0.3); // Mirror handle 30%
                peerProgressBar.setProgress(progress * 0.3);   // Peer handle 30%
            });
        }
    }

    /**
     * Tuần 3: Shake animation khi có lỗi (ErrorCallback hoặc SHA-256 verification fail)
     * Rung lắc overall progress bar để alert user
     */
    public void triggerShakeAnimation() {
        Platform.runLater(() -> {
            TranslateTransition shake = new TranslateTransition(Duration.millis(100), overallProgressBar);
            shake.setFromX(0);
            shake.setToX(10);
            shake.setCycleCount(6);
            shake.setAutoReverse(true);
            shake.play();
        });
    }
}
