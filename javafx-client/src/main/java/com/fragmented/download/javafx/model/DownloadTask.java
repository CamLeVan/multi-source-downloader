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
    private final StringProperty details = new SimpleStringProperty(""); // Speed, ETA, Size
    private final Scheduler scheduler;
    private Object streamingServer; // Store as Object to avoid dependency cycle if not needed, or better import it.
    private final ObservableList<SourceProgress> sourceProgresses = FXCollections.observableArrayList();

    // Speed calculation
    private long lastBytesDownloaded = 0;
    private long lastUpdateTime = System.currentTimeMillis();

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

    public StringProperty detailsProperty() {
        return details;
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
        double currentProgress = scheduler.getProgress();
        progress.set(currentProgress);
        
        // Calculate Speed & ETA
        long currentBytes = scheduler.getDownloadedBytes();
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - lastUpdateTime;
        
        if (timeDiff >= 1000) { // Update speed every second
            long bytesDiff = currentBytes - lastBytesDownloaded;
            double speed = bytesDiff / (timeDiff / 1000.0); // Bytes per second
            
            // Calculate ETA
            long totalBytes = scheduler.getManifest().getFileSize();
            long remainingBytes = totalBytes - currentBytes;
            long etaSeconds = (speed > 0) ? (long) (remainingBytes / speed) : 0;
            
            // Format details string
            String speedStr = formatSpeed(speed);
            String etaStr = formatDuration(etaSeconds);
            String sizeStr = formatSize(currentBytes, totalBytes);
            
            if (currentProgress >= 1.0) {
                details.set("Completed | " + sizeStr);
                status.set("Finished");
            } else {
                details.set(String.format("%s | ETA: %s | %s", speedStr, etaStr, sizeStr));
            }
            
            lastBytesDownloaded = currentBytes;
            lastUpdateTime = currentTime;
        }
    }
    
    private String formatSpeed(double bytesPerSecond) {
        if (bytesPerSecond < 1024) return String.format("%.0f B/s", bytesPerSecond);
        if (bytesPerSecond < 1024 * 1024) return String.format("%.1f KB/s", bytesPerSecond / 1024);
        return String.format("%.1f MB/s", bytesPerSecond / (1024 * 1024));
    }
    
    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (minutes < 60) return String.format("%dm %ds", minutes, remainingSeconds);
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        return String.format("%dh %dm", hours, remainingMinutes);
    }
    
    private String formatSize(long current, long total) {
        return formatBytes(current) + " / " + formatBytes(total);
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
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

