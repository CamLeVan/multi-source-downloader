package com.fragmented.download.admin.model;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Model for file swarm information (P2P)
 */
public class FileSwarm {
    private final StringProperty fileId = new SimpleStringProperty();
    private final IntegerProperty seeders = new SimpleIntegerProperty(0);
    private final ObservableList<String> peers = FXCollections.observableArrayList();
    private final StringProperty health = new SimpleStringProperty("Unknown");

    public FileSwarm() {}

    public FileSwarm(String fileId, int seeders) {
        this.fileId.set(fileId);
        this.seeders.set(seeders);
        updateHealth();
    }

    // Getters
    public String getFileId() { return fileId.get(); }
    public StringProperty fileIdProperty() { return fileId; }
    public void setFileId(String value) { fileId.set(value); }

    public int getSeeders() { return seeders.get(); }
    public IntegerProperty seedersProperty() { return seeders; }
    public void setSeeders(int value) {
        seeders.set(value);
        updateHealth();
    }

    public ObservableList<String> getPeers() { return peers; }
    public void setPeers(java.util.List<String> peerList) {
        peers.setAll(peerList);
        setSeeders(peerList.size());
    }

    public String getHealth() { return health.get(); }
    public StringProperty healthProperty() { return health; }

    private void updateHealth() {
        int s = seeders.get();
        if (s > 5) {
            health.set("Healthy");
        } else if (s > 0) {
            health.set("Stable");
        } else {
            health.set("Critical");
        }
    }
}

