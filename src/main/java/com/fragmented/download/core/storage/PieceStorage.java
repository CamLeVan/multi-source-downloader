package com.fragmented.download.core.storage;

import java.io.IOException;

public interface PieceStorage {
    void writePiece(int pieceId, byte[] data, long offset) throws IOException;

    byte[] readPiece(int pieceId, long offset, int length) throws IOException;

    void setOnReadTrigger(OnReadTrigger trigger);
}
