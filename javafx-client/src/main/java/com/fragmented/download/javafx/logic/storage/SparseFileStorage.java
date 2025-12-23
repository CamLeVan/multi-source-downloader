package com.fragmented.download.javafx.logic.storage;

import com.fragmented.download.core.storage.PieceStorage;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.ByteBuffer;

/**
 * Handles the creation of large, sparse files and writing pieces to them in a
 * thread-safe manner.
 */
public class SparseFileStorage implements PieceStorage {

    /**
     * Creates a sparse file of a given size. This pre-allocates the file size on
     * the filesystem
     * without writing any actual data to it, making it very fast.
     *
     * @param path      The absolute path of the file to create.
     * @param totalSize The total size of the file in bytes.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void createSparseFile(String path, long totalSize) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path, "rw")) {
            file.setLength(totalSize);
        }
    }

    /**
     * Writes a piece of data (a byte array) to the file at a specific offset.
     * This method is thread-safe. It uses a FileChannel to acquire an exclusive
     * lock
     * only on the region of the file being written to, allowing other threads to
     * write
     * to different parts of the file concurrently.
     *
     * @param path   The absolute path of the file to write to.
     * @param offset The starting position in the file to write the data.
     * @param data   The byte array of data to write.
     * @throws IOException if an I/O error occurs, or if the lock cannot be
     *                     acquired.
     */
    @Override
    public void writePiece(String path, long offset, byte[] data) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path, "rw");
                FileChannel channel = file.getChannel()) {

            FileLock lock = null;
            try {
                // Attempt to acquire an exclusive lock on the specific region
                lock = channel.tryLock(offset, data.length, false);

                if (lock == null) {
                    // This can happen if another thread or process has locked this region.
                    // For a download manager, this might indicate a problem or a need to retry.
                    throw new IOException("Could not acquire exclusive lock on file region. Offset: " + offset
                            + ", Size: " + data.length);
                }

                // If the lock is acquired, move to the offset and write the data
                file.seek(offset);
                file.write(data);

            } finally {
                // ALWAYS release the lock in a finally block to prevent deadlocks
                if (lock != null && lock.isValid()) {
                    lock.release();
                }
            }
        }
    }

    /**
     * Reads a piece of data from the file at a specific offset.
     * This method opens the file in read-only mode, seeks to the correct position,
     * reads the specified number of bytes, and then closes the file.
     *
     * @param path   The absolute path of the file to read from.
     * @param offset The starting position in the file to read from.
     * @param length The number of bytes to read.
     * @return The byte array containing the piece data.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public byte[] readPiece(String path, long offset, int length) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path, "r");
                FileChannel channel = file.getChannel()) {

            // Use retry logic for acquiring lock
            FileLock lock = null;
            try {
                // Try to acquire SHARED lock (waiting up to a bit)
                // Note: channel.lock(...) blocks until lock is available
                lock = channel.lock(offset, length, true);

                ByteBuffer buffer = ByteBuffer.allocate(length);
                int bytesRead = channel.read(buffer, offset);

                if (bytesRead < length) {
                    // Handle partial read if necessary, though readFully is preferred usually.
                    // For sparse files, unwritten areas reads as 0, which is fine.
                    // But we want a byte array of exact length.
                }
                return buffer.array();

            } catch (Exception e) {
                // Return simple read if lock fails or other issues
                byte[] data = new byte[length];
                file.seek(offset);
                file.readFully(data);
                return data;
            } finally {
                if (lock != null && lock.isValid()) {
                    lock.release();
                }
            }
        }
    }
}
