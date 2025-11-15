package com.fragmented.download.javafx.logic;

import com.fragmented.download.core.client.DownloadClient;
import com.fragmented.download.core.client.ErrorCallback;
import com.fragmented.download.core.model.DownloadState;
import com.fragmented.download.core.model.PieceModel;
import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.core.storage.PieceStorage;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A worker responsible for downloading a single piece of the file.
 * It uses the DownloadClient to perform the download, writes the data to storage,
 * and updates the overall download state.
 */
public class DownloadWorker {

    private final DownloadClient downloadClient;
    private final ErrorCallback errorCallback;
    private final PieceStorage pieceStorage;
    private final IStateStorage stateStorage;
    private final String localFilePath;
    private final String fileId;
    private final long pieceSize;
    private final DownloadState downloadState;

    public DownloadWorker(DownloadClient downloadClient, ErrorCallback errorCallback,
                          PieceStorage pieceStorage, IStateStorage stateStorage,
                          String localFilePath, String fileId, long pieceSize, DownloadState downloadState) {
        this.downloadClient = downloadClient;
        this.errorCallback = errorCallback;
        this.pieceStorage = pieceStorage;
        this.stateStorage = stateStorage;
        this.localFilePath = localFilePath;
        this.fileId = fileId;
        this.pieceSize = pieceSize;
        this.downloadState = downloadState;
    }

    /**
     * Starts the asynchronous download of a given piece. On success, it writes the
     * data to the sparse file and updates the download state.
     *
     * @param piece           The piece to download.
     * @param successCallback A consumer that will be called with the downloaded byte data on success,
     *                        after the data has been written to storage.
     */
    public void download(PieceModel piece, Consumer<byte[]> successCallback) {
        CompletableFuture<byte[]> downloadFuture = downloadClient.downloadPiece(piece);

        downloadFuture.whenComplete((data, throwable) -> {
            if (throwable != null) {
                // If an error occurred, invoke the general error callback.
                errorCallback.onDownloadFailed(piece, throwable);
            } else {
                try {
                    // Verify SHA-256 hash before writing to disk
                    String calculatedHash = calculateSHA256(data);
                    if (!calculatedHash.equalsIgnoreCase(piece.getSha256())) {
                        throw new IOException("Hash mismatch for piece " + piece.getId() +
                                ". Expected: " + piece.getSha256() + ", Got: " + calculatedHash);
                    }

                    // 1. Write the downloaded piece to the file
                    long offset = (long) piece.getId() * pieceSize;
                    pieceStorage.writePiece(localFilePath, offset, data);

                    // 2. Update the download state and save it
                    // Synchronize on the shared state object to prevent concurrent modification issues
                    synchronized (downloadState) {
                        downloadState.setPieceCompleted(piece.getId());
                        stateStorage.saveState(downloadState, fileId);
                    }

                    // 3. On success, invoke the specific success callback with the data (e.g., for UI updates).
                    successCallback.accept(data);

                } catch (IOException | NoSuchAlgorithmException e) {
                    // Handle errors during file writing or state saving
                    errorCallback.onDownloadFailed(piece, e);
                }
            }
        });
    }

    /**
     * Calculates the SHA-256 hash of the given byte array.
     *
     * @param data The byte array to hash.
     * @return The SHA-256 hash as a hexadecimal string.
     * @throws NoSuchAlgorithmException If the SHA-256 algorithm is not available.
     */
    private String calculateSHA256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedhash = digest.digest(data);
        return bytesToHex(encodedhash);
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
