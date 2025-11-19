package com.fragmented.download.javafx.model;

import com.google.gson.annotations.SerializedName;
import javafx.beans.property.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model để hiển thị thông tin file trong UI
 * Phase 1: Metadata display
 * 
 * Note: Gson deserialization cần fields tạm thời (không phải Properties)
 */
public class FileInfo {
    // Temporary fields for Gson deserialization
    @SerializedName("fileName")
    private String _fileName;
    
    @SerializedName("size")
    private Long _size;
    
    @SerializedName("lastModified")
    private Object _lastModifiedObj; // Spring Boot serialize LocalDateTime thành array [year, month, day, ...]
    
    @SerializedName("mirrorsCount")
    private Integer _mirrorsCount;
    
    @SerializedName("hasManifest")
    private Boolean _hasManifest;

    // JavaFX Properties (sẽ được set sau khi deserialize)
    private final StringProperty fileName = new SimpleStringProperty();
    private final LongProperty size = new SimpleLongProperty();
    private final ObjectProperty<LocalDateTime> lastModified = new SimpleObjectProperty<>();
    private final IntegerProperty mirrorsCount = new SimpleIntegerProperty();
    private final BooleanProperty hasManifest = new SimpleBooleanProperty();

    public FileInfo() {
        // Default constructor for Gson
    }

    /**
     * Callback sau khi Gson deserialize - chuyển temp fields sang Properties
     */
    public void initializeFromDeserialized() {
        if (_fileName != null) fileName.set(_fileName);
        if (_size != null) size.set(_size);
        if (_mirrorsCount != null) mirrorsCount.set(_mirrorsCount);
        if (_hasManifest != null) hasManifest.set(_hasManifest);
        
        // Parse LocalDateTime từ Spring Boot format
        if (_lastModifiedObj != null) {
            try {
                LocalDateTime date = null;
                
                // Spring Boot serialize LocalDateTime thành array: [2024, 11, 17, 23, 0, 0]
                if (_lastModifiedObj instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<Number> dateArray = (java.util.List<Number>) _lastModifiedObj;
                    if (dateArray.size() >= 6) {
                        date = LocalDateTime.of(
                            dateArray.get(0).intValue(), // year
                            dateArray.get(1).intValue(), // month
                            dateArray.get(2).intValue(), // day
                            dateArray.get(3).intValue(), // hour
                            dateArray.get(4).intValue(), // minute
                            dateArray.get(5).intValue()  // second
                        );
                    }
                }
                // Hoặc nếu là String (fallback)
                else if (_lastModifiedObj instanceof String) {
                    String dateStr = (String) _lastModifiedObj;
                    date = LocalDateTime.parse(dateStr.replace("Z", ""));
                }
                
                if (date != null) {
                    lastModified.set(date);
                }
            } catch (Exception e) {
                System.err.println("Error parsing date: " + _lastModifiedObj + " - " + e.getMessage());
            }
        }
    }

    public FileInfo(String fileName, long size, LocalDateTime lastModified, int mirrorsCount, boolean hasManifest) {
        this.fileName.set(fileName);
        this.size.set(size);
        this.lastModified.set(lastModified);
        this.mirrorsCount.set(mirrorsCount);
        this.hasManifest.set(hasManifest);
    }

    // Getters
    public String getFileName() {
        return fileName.get();
    }

    public StringProperty fileNameProperty() {
        return fileName;
    }

    public long getSize() {
        return size.get();
    }

    public LongProperty sizeProperty() {
        return size;
    }

    public LocalDateTime getLastModified() {
        return lastModified.get();
    }

    public ObjectProperty<LocalDateTime> lastModifiedProperty() {
        return lastModified;
    }

    public int getMirrorsCount() {
        return mirrorsCount.get();
    }

    public IntegerProperty mirrorsCountProperty() {
        return mirrorsCount;
    }

    public boolean isHasManifest() {
        return hasManifest.get();
    }

    public BooleanProperty hasManifestProperty() {
        return hasManifest;
    }

    // Helper methods for display
    public String getFormattedSize() {
        long bytes = size.get();
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public String getFormattedDate() {
        LocalDateTime date = lastModified.get();
        if (date == null) {
            return "Unknown";
        }
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    @Override
    public String toString() {
        return fileName.get() + " (" + getFormattedSize() + ")";
    }
}

