package com.fragmented.download.core.storage;

import java.io.IOException;

/**
 * An interface for storage operations related to file pieces.
 * This allows for different storage implementations (e.g., in-memory, on-disk).
 */
public interface PieceStorage {

    /**
     * Creates a file of a given size, potentially as a sparse file.
     *
     * @param path      The absolute path of the file to create.
     * @param totalSize The total size of the file in bytes.
     * @throws IOException if an I/O error occurs.
     */
    void createSparseFile(String path, long totalSize) throws IOException;

    /**
     * Writes a piece of data to the storage at a specific offset.
     *
     * @param path   The absolute path of the file to write to.
     * @param offset The starting position in the file to write the data.
     * @param data   The byte array of data to write.
     * @throws IOException if an I/O error occurs.
     */
    void writePiece(String path, long offset, byte[] data) throws IOException;

    /**
     * Reads a piece of data from the storage at a specific offset.
     *
     * @param path   The absolute path of the file to read from.
     * @param offset The starting position in the file to read the data from.
     * @param length The number of bytes to read.
     * @return A byte array containing the data read.
     * @throws IOException if an I/O error occurs.
     */
    byte[] readPiece(String path, long offset, int length) throws IOException;
}

