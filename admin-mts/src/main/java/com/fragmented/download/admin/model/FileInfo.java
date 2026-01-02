package com.fragmented.download.admin.model;

import com.fragmented.download.core.model.FileInfoDTO;
import javafx.beans.property.*;
import java.time.LocalDateTime;

/**
 * JavaFX wrapper for FileInfoDTO
 * Converts DTO to JavaFX Properties for UI binding
 */
public class FileInfo {
    private final StringProperty fileName = new SimpleStringProperty();
    private final LongProperty size = new SimpleLongProperty(0);
    private final StringProperty sizeFormatted = new SimpleStringProperty("0 B");
    private final ObjectProperty<LocalDateTime> lastModified = new SimpleObjectProperty<>();
    private final BooleanProperty hasManifest = new SimpleBooleanProperty(false);

    public FileInfo() {}

    public FileInfo(String fileName, long size, LocalDateTime lastModified, boolean hasManifest) {
        this.fileName.set(fileName);
        this.size.set(size);
        this.sizeFormatted.set(formatBytes(size));
        this.lastModified.set(lastModified);
        this.hasManifest.set(hasManifest);
    }

    /**
     * Create FileInfo from FileInfoDTO
     */
    public FileInfo(FileInfoDTO dto) {
        if (dto != null) {
            this.fileName.set(dto.getFileName());
            this.size.set(dto.getSize());
            this.sizeFormatted.set(dto.getFormattedSize());
            this.lastModified.set(dto.getLastModified());
            this.hasManifest.set(dto.isHasManifest());
        }
    }

    // Getters
    public String getFileName() { return fileName.get(); }
    public StringProperty fileNameProperty() { return fileName; }
    public void setFileName(String value) { fileName.set(value); }

    public long getSize() { return size.get(); }
    public LongProperty sizeProperty() { return size; }
    public void setSize(long value) {
        size.set(value);
        sizeFormatted.set(formatBytes(value));
    }

    public String getSizeFormatted() { return sizeFormatted.get(); }
    public StringProperty sizeFormattedProperty() { return sizeFormatted; }

    public LocalDateTime getLastModified() { return lastModified.get(); }
    public ObjectProperty<LocalDateTime> lastModifiedProperty() { return lastModified; }
    public void setLastModified(LocalDateTime value) { lastModified.set(value); }

    public boolean isHasManifest() { return hasManifest.get(); }
    public BooleanProperty hasManifestProperty() { return hasManifest; }
    public void setHasManifest(boolean value) { hasManifest.set(value); }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

