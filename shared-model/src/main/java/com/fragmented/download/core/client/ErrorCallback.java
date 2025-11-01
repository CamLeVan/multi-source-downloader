package com.fragmented.download.core.client;

import com.fragmented.download.core.model.PieceModel;

/**
 * A functional interface for handling download failures.
 * This serves as a callback mechanism for the download workers to report errors
 * back to the main scheduler or UI controller.
 */
@FunctionalInterface
public interface ErrorCallback {

    /**
     * Method to be invoked when the download of a specific piece fails.
     *
     * @param piece The piece that failed to download.
     * @param cause The Throwable/Exception that caused the failure (e.g., TimeoutException, HashMismatchException).
     */
    void onDownloadFailed(PieceModel piece, Throwable cause);
}
