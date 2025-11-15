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
     * Starts the asynchronous download of a given piece. (Dùng cho UI callback)
     * On success, it writes data and updates state.
     *
     * @param piece           The piece to download.
     * @param successCallback A consumer called on success (e.g., for UI updates).
     */
    public void download(PieceModel piece, Consumer<byte[]> successCallback) {
        CompletableFuture<byte[]> downloadFuture = downloadClient.downloadPiece(piece);

        downloadFuture.whenComplete((data, throwable) -> {
            if (throwable != null) {
                // Nếu lỗi, báo lỗi
                errorCallback.onDownloadFailed(piece, throwable);
            } else {
                try {
                    // *** REFACTORED ***
                    // 1. Gọi phương thức "lõi"
                    handleSuccessfulDownload(piece, data);

                    // 2. Gọi callback thành công
                    successCallback.accept(data);

                } catch (IOException | NoSuchAlgorithmException e) {
                    // Bắt lỗi từ handleSuccessfulDownload (ví dụ: Hash sai)
                    errorCallback.onDownloadFailed(piece, e);
                }
            }
        });
    }

    /**
     * Starts the asynchronous download of a given piece. (Dùng cho VFS on-demand)
     * Returns a future that completes when the operation is finished.
     *
     * @param piece The piece to download.
     * @return A {@link CompletableFuture<Void>} that completes when the operation is finished.
     */
    public CompletableFuture<Void> download(PieceModel piece) {
        CompletableFuture<Void> completionFuture = new CompletableFuture<>();
        CompletableFuture<byte[]> downloadFuture = downloadClient.downloadPiece(piece);

        downloadFuture.whenComplete((data, throwable) -> {
            if (throwable != null) {
                // Nếu lỗi, báo lỗi VÀ báo lỗi cho Future
                errorCallback.onDownloadFailed(piece, throwable);
                completionFuture.completeExceptionally(throwable);
            } else {
                try {
                    // *** REFACTORED ***
                    // 1. Gọi phương thức "lõi"
                    handleSuccessfulDownload(piece, data);

                    // 2. Báo cho Future là đã xong
                    completionFuture.complete(null);

                } catch (IOException | NoSuchAlgorithmException e) {
                    // Bắt lỗi từ handleSuccessfulDownload (ví dụ: Hash sai)
                    errorCallback.onDownloadFailed(piece, e);
                    completionFuture.completeExceptionally(e);
                }
            }
        });
        return completionFuture;
    }

    /**
     * *** PHƯƠNG THỨC "LÕI" MỚI ***
     * Logic cốt lõi (hash, write, state) được gọi bởi cả hai phương thức public download.
     * Điều này tuân thủ nguyên tắc DRY (Don't Repeat Yourself).
     */
    private void handleSuccessfulDownload(PieceModel piece, byte[] data)
            throws IOException, NoSuchAlgorithmException {

        // 1. Xác thực (Task Tuần 3)
        String calculatedHash = calculateSHA256(data);
        if (!calculatedHash.equalsIgnoreCase(piece.getSha256())) {
            throw new IOException("Hash mismatch for piece " + piece.getId() +
                    ". Expected: " + piece.getSha256() + ", Got: " + calculatedHash);
        }

        // 2. Ghi file (Task Tuần 1)
        long offset = (long) piece.getId() * pieceSize;
        // (Chúng ta đã xác nhận SparseFileStorage.writePiece là thread-safe)
        pieceStorage.writePiece(localFilePath, offset, data);

        // 3. Cập nhật trạng thái (Vá lỗi đa luồng Tuần 2)
        // (Chúng ta đã xác nhận DownloadState KHÔNG thread-safe, nên phải sync ở đây)
        synchronized (downloadState) {
            downloadState.setPieceCompleted(piece.getId());
            stateStorage.saveState(downloadState, fileId);
        }
    }


    /**
     * Calculates the SHA-256 hash of the given byte array.
     */
    private String calculateSHA256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedhash = digest.digest(data);
        return bytesToHex(encodedhash);
    }

    /**
     * Converts a byte array into a hexadecimal string.
     */
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