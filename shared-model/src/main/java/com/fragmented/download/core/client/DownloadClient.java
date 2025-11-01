package com.fragmented.download.core.client;

import com.fragmented.download.core.model.PieceModel;

import java.util.concurrent.CompletableFuture;

/**
 * Defines the contract for a client capable of downloading file pieces.
 * This interface is a critical synchronization point between the backend logic (which might implement this)
 * and the client-side scheduler (which will use this).
 */
public interface DownloadClient {

    /**
     * Asynchronously downloads a single piece of a file from one of its available sources.
     *
     * @param piece The metadata of the piece to be downloaded, including its sources.
     * @return A CompletableFuture which, upon completion, will hold the raw byte data of the piece.
     *         The future will complete exceptionally if the download fails from all sources.
     */
    CompletableFuture<byte[]> downloadPiece(PieceModel piece);
}
