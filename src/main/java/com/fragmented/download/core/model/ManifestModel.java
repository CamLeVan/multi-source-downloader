package com.fragmented.download.core.model;

import java.util.List;

public class ManifestModel {
    private long fileSize;
    private long pieceSize;
    private List<PieceModel> pieces;

    public ManifestModel(long fileSize, long pieceSize, List<PieceModel> pieces) {
        this.fileSize = fileSize;
        this.pieceSize = pieceSize;
        this.pieces = pieces;
    }

    // Getters and Setters
    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getPieceSize() {
        return pieceSize;
    }

    public void setPieceSize(long pieceSize) {
        this.pieceSize = pieceSize;
    }

    public List<PieceModel> getPieces() {
        return pieces;
    }

    public void setPieces(List<PieceModel> pieces) {
        this.pieces = pieces;
    }
}
