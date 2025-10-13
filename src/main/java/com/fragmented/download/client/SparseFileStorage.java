package com.fragmented.download.client;

import com.fragmented.download.core.storage.OnReadTrigger;
import com.fragmented.download.core.storage.PieceStorage;

import java.io.IOException;
import java.io.RandomAccessFile;

public class SparseFileStorage implements PieceStorage {

    private final RandomAccessFile randomAccessFile;
    private OnReadTrigger onReadTrigger;

    public SparseFileStorage(String filePath, long totalSize) throws IOException {
        this.randomAccessFile = new RandomAccessFile(filePath, "rw");
        this.randomAccessFile.setLength(totalSize);
    }

    @Override
    public synchronized void writePiece(int pieceId, byte[] data, long offset) throws IOException {
        randomAccessFile.seek(offset);
        randomAccessFile.write(data);
    }

    @Override
    public synchronized byte[] readPiece(int pieceId, long offset, int length) throws IOException {
        byte[] buffer = new byte[length];
        randomAccessFile.seek(offset);
        int bytesRead = randomAccessFile.read(buffer);

        if (bytesRead == length) {
            return buffer;
        }

        if (bytesRead == -1) {
            return new byte[0];
        }

        // Return a smaller buffer if end of file is reached unexpectedly
        byte[] smallerBuffer = new byte[bytesRead];
        System.arraycopy(buffer, 0, smallerBuffer, 0, bytesRead);
        return smallerBuffer;
    }

    @Override
    public void setOnReadTrigger(OnReadTrigger trigger) {
        this.onReadTrigger = trigger;
    }

    public void close() throws IOException {
        if (randomAccessFile != null) {
            randomAccessFile.close();
        }
    }
}
