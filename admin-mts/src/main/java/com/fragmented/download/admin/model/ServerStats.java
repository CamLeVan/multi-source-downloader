package com.fragmented.download.admin.model;

import javafx.beans.property.*;

/**
 * Model for server statistics
 * Used in Dashboard to display server overview
 */
public class ServerStats {
    private final IntegerProperty totalFiles = new SimpleIntegerProperty(0);
    private final IntegerProperty totalPeers = new SimpleIntegerProperty(0);
    private final LongProperty totalSize = new SimpleLongProperty(0);
    private final StringProperty totalSizeFormatted = new SimpleStringProperty("0 B");
    private final LongProperty diskFree = new SimpleLongProperty(0);
    private final LongProperty diskTotal = new SimpleLongProperty(0);
    private final StringProperty diskFreeFormatted = new SimpleStringProperty("0 B");
    private final StringProperty diskTotalFormatted = new SimpleStringProperty("0 B");
    private final DoubleProperty diskUsagePercent = new SimpleDoubleProperty(0);

    // Getters
    public int getTotalFiles() { return totalFiles.get(); }
    public IntegerProperty totalFilesProperty() { return totalFiles; }
    public void setTotalFiles(int value) { totalFiles.set(value); }

    public int getTotalPeers() { return totalPeers.get(); }
    public IntegerProperty totalPeersProperty() { return totalPeers; }
    public void setTotalPeers(int value) { totalPeers.set(value); }

    public long getTotalSize() { return totalSize.get(); }
    public LongProperty totalSizeProperty() { return totalSize; }
    public void setTotalSize(long value) { totalSize.set(value); }

    public String getTotalSizeFormatted() { return totalSizeFormatted.get(); }
    public StringProperty totalSizeFormattedProperty() { return totalSizeFormatted; }
    public void setTotalSizeFormatted(String value) { totalSizeFormatted.set(value); }

    public long getDiskFree() { return diskFree.get(); }
    public LongProperty diskFreeProperty() { return diskFree; }
    public void setDiskFree(long value) { diskFree.set(value); }

    public long getDiskTotal() { return diskTotal.get(); }
    public LongProperty diskTotalProperty() { return diskTotal; }
    public void setDiskTotal(long value) { diskTotal.set(value); }

    public String getDiskFreeFormatted() { return diskFreeFormatted.get(); }
    public StringProperty diskFreeFormattedProperty() { return diskFreeFormatted; }
    public void setDiskFreeFormatted(String value) { diskFreeFormatted.set(value); }

    public String getDiskTotalFormatted() { return diskTotalFormatted.get(); }
    public StringProperty diskTotalFormattedProperty() { return diskTotalFormatted; }
    public void setDiskTotalFormatted(String value) { diskTotalFormatted.set(value); }

    public double getDiskUsagePercent() { return diskUsagePercent.get(); }
    public DoubleProperty diskUsagePercentProperty() { return diskUsagePercent; }
    public void setDiskUsagePercent(double value) { diskUsagePercent.set(value); }
}

