package com.fragmented.download.javafx.logic;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.fragmented.download.core.client.DownloadClient;
import com.fragmented.download.core.client.ErrorCallback;
import com.fragmented.download.core.model.DownloadState;
import com.fragmented.download.core.model.PieceModel;
import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.core.storage.PieceStorage;

/**
 * A worker responsible for downloading a single piece of the file.
 * It uses the DownloadClient to perform the download, writes the data to
 * storage,
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
     * @param successCallback A consumer that will be called with the downloaded
     *                        byte data on success,
     *                        after the data has been written to storage.
     * @param retryCallback   Optional callback when hash mismatch occurs, to retry
     *                        from different source.
     *                        If null, will call errorCallback instead.
     */
    /**
     * Starts the asynchronous download of a given piece.
     * Returns a CompletableFuture that completes when the download (and writing) is
     * finished.
     */
    public CompletableFuture<Void> download(PieceModel piece, Consumer<byte[]> successCallback,
            Consumer<PieceModel> retryCallback) {
        return downloadInternal(piece, successCallback, retryCallback, 0);
    }

    /**
     * Overloaded method for backward compatibility
     */
    public CompletableFuture<Void> download(PieceModel piece, Consumer<byte[]> successCallback) {
        return download(piece, successCallback, null);
    }

    /**
     * Internal download method with retry support for hash mismatch
     */
    private CompletableFuture<Void> downloadInternal(PieceModel piece, Consumer<byte[]> successCallback,
            Consumer<PieceModel> retryCallback, int retryCount) {
        CompletableFuture<Void> resultFuture = new CompletableFuture<>();

        // Helper method to fail the future safely
        Runnable failTask = () -> {
            // Already handled by callbacks, just complete the future normally
            // so the worker can move on to the next task (or exceptionally if you want to
            // stop).
            // Here we choose to complete normally so the scheduler doesn't crash.
            resultFuture.complete(null);
        };

        // Giới hạn số lần retry để tránh loop vô hạn
        if (retryCount > piece.getSources().size()) {
            errorCallback.onDownloadFailed(piece,
                    new IOException("Hash mismatch: exceeded max retry attempts for piece " + piece.getId()));
            resultFuture.complete(null);
            return resultFuture;
        }

        CompletableFuture<byte[]> downloadFuture = downloadClient.downloadPiece(piece);

        downloadFuture.whenComplete((data, throwable) -> {
            if (throwable != null) {
                errorCallback.onDownloadFailed(piece, throwable);
                resultFuture.complete(null);
            } else {
                try {
                    // Verify SHA-256 hash
                    String calculatedHash = calculateSHA256(data);

                    if (!calculatedHash.equalsIgnoreCase(piece.getSha256())) {
                        // ... Log hash mismatch ...

                        // Check retry
                        if (piece.getSources().size() > 1 && retryCallback != null) {
                            // ... Log retry ...

                            // Re-queue logic handled by RetryCallback in Scheduler usually,
                            // but here we might need recursive retry logic inside Worker or delegating
                            // back.
                            // Current logic delegates back to Scheduler via callback.
                            // Use recursive call effectively for internal retry?
                            // The original code called retryCallback.accept(retryPiece).
                            // If we want to wait for that retry, it gets complicated.

                            // SAFE APPROACH for this refactor:
                            // Execute the callback (which queues new task in Scheduler)
                            // and mark CURRENT task as finished.
                            retryCallback.accept(new PieceModel(piece.getId(), piece.getSha256(),
                                    new ArrayList<>(piece.getSources().subList(1, piece.getSources().size()))));

                            resultFuture.complete(null);
                            return;
                        } else {
                            throw new IOException("Hash mismatch - no more sources");
                        }
                    }

                    // Hash verified - write to disk
                    long offset = (long) piece.getId() * pieceSize;
                    pieceStorage.writePiece(localFilePath, offset, data);

                    synchronized (downloadState) {
                        downloadState.setPieceCompleted(piece.getId());
                        stateStorage.saveState(downloadState, fileId);
                    }

                    successCallback.accept(data);
                    resultFuture.complete(null); // Mark as done

                } catch (Exception e) {
                    errorCallback.onDownloadFailed(piece, e);
                    resultFuture.complete(null);
                }
            }
        });

        return resultFuture;
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

    private String extractIPFromUrl(String url) {
        try {
            if (url.startsWith("http://")) {
                url = url.substring(7);
            } else if (url.startsWith("https://")) {
                url = url.substring(8);
            }
            int colonIndex = url.indexOf(':');
            if (colonIndex > 0) {
                return url.substring(0, colonIndex);
            }
            int slashIndex = url.indexOf('/');
            if (slashIndex > 0) {
                return url.substring(0, slashIndex);
            }
            return url;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
