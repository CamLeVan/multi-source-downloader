package com.fragmented.download.p2p.dto;

public class AnnounceRequest {
    private String fileId;
    private String peerId;
    private int port;

    // Default constructor for JSON deserialization
    public AnnounceRequest() {
    }

    // Getters and Setters
    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getPeerId() {
        return peerId;
    }

    public void setPeerId(String peerId) {
        this.peerId = peerId;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
