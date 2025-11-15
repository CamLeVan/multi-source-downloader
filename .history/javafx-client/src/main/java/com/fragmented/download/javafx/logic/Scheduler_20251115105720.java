package com.fragmented.download.javafx.logic;


import com.fragmented.download.core.client.DownloadClient;
import com.fragmented.download.core.client.ErrorCallback;
import com.fragmented.download.core.model.DownloadState;
import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.core.model.PieceModel;
import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.core.storage.PieceStorage;

import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Manages the queue of pieces to be downloaded, orchestrates the download process,
 * and resumes downloads from a previously saved state.
 */
public class Scheduler {

    private enum SchedulerState {
        IDLE,
        RUNNING,
        PAUSED,
        FINISHED
    }

    private final Queue<PieceModel> pieceQueue;
    private final ManifestModel manifest;
    private final DownloadClient downloadClient;
    private final ErrorCallback errorCallback;
    private final int numberOfWorkers;
    private ExecutorService workerExecutor; // Made non-final to allow re-creation
    private final PieceStorage pieceStorage;
    private final IStateStorage stateStorage;
    private final String localFilePath;
    private final String fileId;
    private final DownloadState downloadState;

    private volatile SchedulerState state = SchedulerState.IDLE; // State management

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
        long completedPieceCount = this.downloadState.getCompletedPieceCount();
        this.downloadedBytes.set(completedPieceCount * manifest.getPieceSize());

        // Filter out pieces that are already completed
        this.pieceQueue = manifest.getPieces().stream()
                .filter(p -> !this.downloadState.isPieceCompleted(p.getId()))
                .collect(Collectors.toCollection(ConcurrentLinkedQueue::new));

        if (pieceQueue.isEmpty()) {
            this.state = SchedulerState.FINISHED;
        }
    }

    private void run() {
        System.out.println("Scheduler started for file of size: " + manifest.getFileSize());
        System.out.println("Total pieces to download: " + pieceQueue.size() + " (already completed: " + downloadState.getCompletedPieceCount() + ")");

        if (pieceQueue.isEmpty()) {
            System.out.println("Download is already complete.");
            state = SchedulerState.FINISHED;
            return;
        }

        this.workerExecutor = Executors.newFixedThreadPool(numberOfWorkers);
        this.state = SchedulerState.RUNNING;
        submitWorkers();
    }

    public void start() {
        if (state == SchedulerState.IDLE) {
            run();
        } else {
            System.out.println("Scheduler can only be started once. Use resume() to continue a paused download.");
        }
    }

    private void submitWorkers() {
        for (int i = 0; i < numberOfWorkers; i++) {
            DownloadWorker worker = new DownloadWorker(downloadClient, errorCallback, pieceStorage, stateStorage,
                    localFilePath, fileId, manifest.getPieceSize(), downloadState);

            workerExecutor.submit(() -> {
                while (state == SchedulerState.RUNNING && !pieceQueue.isEmpty()) {
                    PieceModel piece = pieceQueue.poll();
                    if (piece != null) {
                        worker.download(piece, (data) -> {
                            long totalDownloaded = downloadedBytes.addAndGet(data.length);
                            System.out.println("Downloaded piece " + piece.getId() + ". Total downloaded: " + totalDownloaded);
                        });
                    }
                }
                // Check if the download finished naturally
                if (pieceQueue.isEmpty()) {
                    state = SchedulerState.FINISHED;
                    System.out.println("All pieces downloaded. Download finished.");
                }
            });
        }
    }

    public void pause() {
        if (state == SchedulerState.RUNNING) {
            System.out.println("Pausing scheduler...");
            state = SchedulerState.PAUSED;
            // This will stop workers from picking up new tasks because the loop condition will fail.
            // It allows currently downloading pieces to finish.
            workerExecutor.shutdown(); // Does not accept new tasks.
            System.out.println("Scheduler paused. Workers will stop after finishing current piece.");
        }
    }

    public void resume() {
        if (state == SchedulerState.PAUSED) {
            System.out.println("Resuming scheduler...");
            // Re-initialize the executor and submit workers to process the remaining queue
            run();
        }
    }

    /**
     * @return The unique identifier for the file being downloaded.
     */
    public String getFileId() {
        return fileId;
    }

    /**
     * Downloads a single piece on-demand. This is primarily used by `downloadPiecesOnDemand`.
     *
     * @param piece The piece to download.
     * @return A CompletableFuture that completes when the download is finished.
     */
    private CompletableFuture<Void> downloadOnDemand(PieceModel piece) {
        DownloadWorker worker = new DownloadWorker(downloadClient, errorCallback, pieceStorage, stateStorage,
                localFilePath, fileId, manifest.getPieceSize(), downloadState);
        return worker.download(piece);
    }

    /**
     * Downloads a list of pieces on-demand and returns a CompletableFuture that completes when all are done.
     * This is the key method for the VFS to call.
     */
    public CompletableFuture<Void> downloadPiecesOnDemand(List<PieceModel> pieces) {
        List<CompletableFuture<Void>> futures = pieces.stream()
                // Filter out pieces that might already be completed to avoid re-downloading
                .filter(p -> !downloadState.isPieceCompleted(p.getId()))
                .map(this::downloadOnDemand)
                .collect(Collectors.toList());

        // Return a single future that completes when all individual download futures are complete.
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Convenience method to download a range of pieces by their indices.
     */
    public CompletableFuture<Void> downloadPiecesOnDemand(int startIndex, int endIndex) {
        // Ensure indices are within the bounds of the piece list
        int totalPieces = manifest.getPieces().size();
        if (startIndex < 0 || endIndex >= totalPieces || startIndex > endIndex) {
            return CompletableFuture.failedFuture(new IndexOutOfBoundsException("Invalid piece index range"));
        }
        List<PieceModel> piecesToDownload = manifest.getPieces().subList(startIndex, endIndex + 1);
        return downloadPiecesOnDemand(piecesToDownload);
    }

    public double getProgress() {
        if (manifest.getFileSize() <= 0) {
            return 0;
        }
        return (double) downloadState.getCompletedPieceCount() / downloadState.getTotalPieces();
    }

    public void shutdown() {
        workerExecutor.shutdownNow();
    }
}
