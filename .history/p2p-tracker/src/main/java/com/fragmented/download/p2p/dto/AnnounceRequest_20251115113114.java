package com.fragmented.download.p2p.dto;

public class AnnounceRequest {
    private String fileId;
    private String peerId;
    private int port;
    private int pieceId; // ID of the piece the peer now has

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

    public int getPieceId() {
        return pieceId;
    }

    public void setPieceId(int pieceId) {
        this.pieceId = pieceId;
    }
}
