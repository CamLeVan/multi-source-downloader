package com.fragmented.download.core.model;

import java.util.BitSet;

/**
 * Represents the download state of a file, tracking which pieces have been completed.
 * This class uses a BitSet for efficient storage of piece completion status and is designed
 * to be easily serialized to JSON by representing the BitSet as a byte array.
 */
public class DownloadState {

    // Serialized form of the BitSet for persistence.
    private byte[] downloadedPieces;
    private int totalPieces;

    // Transient BitSet for runtime logic. It's reconstructed from the byte array.
    private transient BitSet bitSet;

    /**
     * Private constructor for frameworks like Gson.
     */
    private DownloadState() {}

    /**
     * Creates a new, empty download state for a file with a given number of pieces.
     * @param totalPieces The total number of pieces the file is divided into.
     */
    public DownloadState(int totalPieces) {
        this.totalPieces = totalPieces;
        this.bitSet = new BitSet(totalPieces);
        this.downloadedPieces = this.bitSet.toByteArray();
    }

    /**
     * Lazily initializes the transient BitSet from the byte array after deserialization.
     * @return The active BitSet.
     */
    private BitSet getBitSet() {
        if (bitSet == null) {
            bitSet = BitSet.valueOf(downloadedPieces);
        }
        return bitSet;
    }

    /**
     * Marks a specific piece as completed.
     * @param pieceIndex The index of the completed piece.
     */
    public void setPieceCompleted(int pieceIndex) {
        getBitSet().set(pieceIndex);
        // Ensure the byte array is updated for the next save operation.
        this.downloadedPieces = getBitSet().toByteArray();
    }

    /**
     * Checks if a specific piece has been completed.
     * @param pieceIndex The index of the piece to check.
     * @return true if the piece is marked as complete, false otherwise.
     */
    public boolean isPieceCompleted(int pieceIndex) {
        return getBitSet().get(pieceIndex);
    }

    /**
     * @return The total number of pieces for the file.
     */
    public int getTotalPieces() {
        return totalPieces;
    }

    /**
     * @return The number of pieces that have been successfully downloaded.
     */
    public int getCompletedPieceCount() {
        return getBitSet().cardinality();
    }

    /**
     * @return The raw byte array representing the set of downloaded pieces.
     */
    public byte[] getDownloadedPieces() {
        return downloadedPieces;
    }
}
