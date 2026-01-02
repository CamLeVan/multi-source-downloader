package com.fragmented.download.core.model;

import java.time.LocalDateTime;

/**
 * Common DTO for file information
 * Used across all modules (origin-server, admin-client, javafx-client)
 * No JavaFX dependencies - pure data transfer object
 */
public class FileInfoDTO {
    private String fileName;
    private long size; // bytes
    private LocalDateTime lastModified;
    private int mirrorsCount; // Số lượng mirrors có sẵn (hiện tại = 0, sẽ tính sau)
    private boolean hasManifest;

    public FileInfoDTO() {
    }

    public FileInfoDTO(String fileName, long size, LocalDateTime lastModified, boolean hasManifest) {
        this.fileName = fileName;
        this.size = size;
        this.lastModified = lastModified;
        this.mirrorsCount = 0; // Default
        this.hasManifest = hasManifest;
    }

    // Getters and Setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public int getMirrorsCount() {
        return mirrorsCount;
    }

    public void setMirrorsCount(int mirrorsCount) {
        this.mirrorsCount = mirrorsCount;
    }

    public boolean isHasManifest() {
        return hasManifest;
    }

    public void setHasManifest(boolean hasManifest) {
        this.hasManifest = hasManifest;
    }

    /**
     * Helper method to format file size
     */
    public String getFormattedSize() {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
}

