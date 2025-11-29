package com.fragmented.download.javafx.model;

import com.fragmented.download.javafx.logic.Scheduler;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Tuần 6: Model đại diện cho một download task trong ListView
 * Sử dụng JavaFX Properties để tự động update UI
 */
public class DownloadTask {

    private final StringProperty fileName = new SimpleStringProperty();
    private final DoubleProperty progress = new SimpleDoubleProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final StringProperty streamingUrl = new SimpleStringProperty(); // Tuần 6: Streaming URL
    private final Scheduler scheduler;
    private Object streamingServer; // Store as Object to avoid dependency cycle if not needed, or better import it.
    private final ObservableList<SourceProgress> sourceProgresses = FXCollections.observableArrayList();

    public DownloadTask(String fileName, Scheduler scheduler) {
        this.fileName.set(fileName);
        this.scheduler = scheduler;
        this.status.set("Downloading");
        
        // Initialize source progress tracking
        sourceProgresses.add(new SourceProgress(SourceProgress.SourceType.ORIGIN, "Origin Server"));
        sourceProgresses.add(new SourceProgress(SourceProgress.SourceType.MIRROR, "Mirror Server"));
        sourceProgresses.add(new SourceProgress(SourceProgress.SourceType.PEER, "Peers"));
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

    public String getStreamingUrl() {
        return streamingUrl.get();
    }

    public StringProperty streamingUrlProperty() {
        return streamingUrl;
    }

    public void setStreamingUrl(String url) {
        this.streamingUrl.set(url);
    }

    public void setStreamingServer(Object server) {
        this.streamingServer = server;
    }

    public Object getStreamingServer() {
        return streamingServer;
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
    
    public ObservableList<SourceProgress> getSourceProgresses() {
        return sourceProgresses;
    }
    
    /**
     * Track bytes downloaded from a specific source
     */
    public void trackSourceBytes(String sourceUrl, long bytes) {
        SourceProgress.SourceType type = detectSourceType(sourceUrl);
        SourceProgress progress = findOrCreateSourceProgress(type, sourceUrl);
        progress.addBytes(bytes);
    }
    
    private SourceProgress.SourceType detectSourceType(String sourceUrl) {
        if (sourceUrl.contains("localhost:8080") || sourceUrl.contains("localhost:8443") || sourceUrl.contains("/files/")) {
            return SourceProgress.SourceType.ORIGIN;
        } else if (sourceUrl.contains("mirror") || sourceUrl.contains("vku.udn.vn")) {
            return SourceProgress.SourceType.MIRROR;
        } else if (sourceUrl.contains("/piece/")) {
            return SourceProgress.SourceType.PEER;
        }
        return SourceProgress.SourceType.ORIGIN; // Default
    }
    
    private SourceProgress findOrCreateSourceProgress(SourceProgress.SourceType type, String sourceUrl) {
        // Tìm source progress theo type
        for (SourceProgress sp : sourceProgresses) {
            if (sp.getSourceType() == type) {
                return sp;
            }
        }
        
        // Nếu không tìm thấy, tạo mới (cho peer có thể có nhiều)
        if (type == SourceProgress.SourceType.PEER) {
            SourceProgress newProgress = new SourceProgress(type, extractPeerName(sourceUrl));
            sourceProgresses.add(newProgress);
            return newProgress;
        }
        
        // Fallback
        return sourceProgresses.get(0);
    }
    
    private String extractPeerName(String peerUrl) {
        // Extract peer address from URL: http://ip:port/piece/...
        try {
            String withoutProtocol = peerUrl.replace("http://", "").replace("https://", "");
            int slashIndex = withoutProtocol.indexOf('/');
            if (slashIndex > 0) {
                return withoutProtocol.substring(0, slashIndex);
            }
            return withoutProtocol;
        } catch (Exception e) {
            return "Peer";
        }
    }
}

