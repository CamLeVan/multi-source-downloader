package com.fragmented.download.admin.model;

import javafx.beans.property.*;

/**
 * Model for peer information
 */
public class PeerInfo {
    private final StringProperty address = new SimpleStringProperty();
    private final StringProperty fileId = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty("Active");

    public PeerInfo() {}

    public PeerInfo(String address, String fileId) {
        this.address.set(address);
        this.fileId.set(fileId);
    }

    // Getters
    public String getAddress() { return address.get(); }
    public StringProperty addressProperty() { return address; }
    public void setAddress(String value) { address.set(value); }

    public String getFileId() { return fileId.get(); }
    public StringProperty fileIdProperty() { return fileId; }
    public void setFileId(String value) { fileId.set(value); }

    public String getStatus() { return status.get(); }
    public StringProperty statusProperty() { return status; }
    public void setStatus(String value) { status.set(value); }
}

