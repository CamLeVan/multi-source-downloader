package com.fragmented.download.javafx.model;

import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Model để track progress của từng source (Origin, Mirror, Peer)
 */
public class SourceProgress {
    
    public enum SourceType {
        ORIGIN("Origin Server"),
        MIRROR("Mirror Server"),
        PEER("Peer");
        
        private final String displayName;
        
        SourceType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private final StringProperty sourceName = new SimpleStringProperty();
    private final LongProperty bytesDownloaded = new SimpleLongProperty(0);
    private final SourceType sourceType;
    
    public SourceProgress(SourceType sourceType, String sourceName) {
        this.sourceType = sourceType;
        this.sourceName.set(sourceName);
    }
    
    public SourceType getSourceType() {
        return sourceType;
    }
    
    public String getSourceName() {
        return sourceName.get();
    }
    
    public StringProperty sourceNameProperty() {
        return sourceName;
    }
    
    public long getBytesDownloaded() {
        return bytesDownloaded.get();
    }
    
    public LongProperty bytesDownloadedProperty() {
        return bytesDownloaded;
    }
    
    public void addBytes(long bytes) {
        bytesDownloaded.set(bytesDownloaded.get() + bytes);
    }
    
    public void reset() {
        bytesDownloaded.set(0);
    }
}

