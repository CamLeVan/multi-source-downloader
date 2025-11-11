package com.fragmented.download.javafx.logic;

import com.fragmented.download.core.client.DownloadClient;
import com.fragmented.download.core.client.ErrorCallback;
import com.fragmented.download.core.model.DownloadState;
import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.core.model.PieceModel;
import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.core.storage.PieceStorage;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Manages the queue of pieces to be downloaded, orchestrates the download process,
 * and resumes downloads from a previously saved state.
 */
public class Scheduler {

    private final Queue<PieceModel> pieceQueue;
    private final ManifestModel manifest;
    private final DownloadClient downloadClient;
    private final ErrorCallback errorCallback;
    private final int numberOfWorkers;
    private final ExecutorService workerExecutor;
    private final PieceStorage pieceStorage;
    private final IStateStorage stateStorage;
    private final String localFilePath;
    private final String fileId;
    private final DownloadState downloadState;

    private final AtomicLong downloadedBytes = new AtomicLong(0);

    public Scheduler(ManifestModel manifest, DownloadClient downloadClient, ErrorCallback errorCallback, int numberOfWorkers,
                     PieceStorage pieceStorage, IStateStorage stateStorage, String localFilePath, String fileId) throws IOException {
        this.manifest = manifest;
        this.downloadClient = downloadClient;
        this.errorCallback = errorCallback;
        this.numberOfWorkers = numberOfWorkers;
        this.pieceStorage = pieceStorage;
        this.stateStorage = stateStorage;
        this.localFilePath = localFilePath;
        this.fileId = fileId;

        // Load or create a new DownloadState
        DownloadState loadedState = this.stateStorage.loadState(fileId);
        if (loadedState == null) {
            this.downloadState = new DownloadState(manifest.getPieces().size());
        } else {
            this.downloadState = loadedState;
        }

        // Initialize downloaded bytes based on the loaded state
        // Note: This is an approximation; it doesn't account for the last piece being smaller.
        // A more accurate way would be to sum the actual sizes of completed pieces.
        long completedPieceCount = this.downloadState.getCompletedPieceCount();
        this.downloadedBytes.set(completedPieceCount * manifest.getPieceSize());

        // Filter out pieces that are already completed
        this.pieceQueue = manifest.getPieces().stream()
                .filter(p -> !this.downloadState.isPieceCompleted(p.getId()))
                .collect(Collectors.toCollection(ConcurrentLinkedQueue::new));

        this.workerExecutor = Executors.newFixedThreadPool(numberOfWorkers);
    }

    public void start() {
        System.out.println("Scheduler started for file of size: " + manifest.getFileSize());
        System.out.println("Total pieces to download: " + pieceQueue.size() + " (already completed: " + downloadState.getCompletedPieceCount() + ")");

        if (pieceQueue.isEmpty()) {
            System.out.println("Download is already complete.");
            // Optionally, trigger a UI update to show completion
            return;
        }

        for (int i = 0; i < numberOfWorkers; i++) {
            DownloadWorker worker = new DownloadWorker(downloadClient, errorCallback, pieceStorage, stateStorage,
                    localFilePath, fileId, manifest.getPieceSize(), downloadState);

            workerExecutor.submit(() -> {
                while (!pieceQueue.isEmpty()) {
                    PieceModel piece = pieceQueue.poll();
                    if (piece != null) {
                        System.out.println("Worker " + Thread.currentThread().getName() + " is downloading piece " + piece.getId());
                        worker.download(piece, (data) -> {
                            long totalDownloaded = downloadedBytes.addAndGet(data.length);
                            System.out.println("Downloaded piece " + piece.getId() + ". Total downloaded: " + totalDownloaded);
                            // UI progress updates are handled by the controller observing the scheduler's progress.
                        });
                    }
                }
                System.out.println("Worker " + Thread.currentThread().getName() + " finished.");
            });
        }
    }

    public double getProgress() {
        if (manifest.getFileSize() <= 0) {
            return 0;
        }
        // For a more accurate progress, we could use the number of completed pieces
        double progress = (double) downloadState.getCompletedPieceCount() / downloadState.getTotalPieces();
        return progress;
        // return (double) downloadedBytes.get() / manifest.getFileSize();
    }

    public void shutdown() {
        workerExecutor.shutdownNow(); // Use shutdownNow to interrupt workers immediately
    }
}
