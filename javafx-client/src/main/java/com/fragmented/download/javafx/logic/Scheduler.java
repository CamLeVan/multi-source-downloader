package com.fragmented.download.javafx.logic;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.fragmented.download.core.client.DownloadClient;
import com.fragmented.download.core.client.ErrorCallback;
import com.fragmented.download.core.model.DownloadState;
import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.core.model.PieceModel;
import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.core.storage.PieceStorage;

/**
 * Manages the queue of pieces to be downloaded, orchestrates the download
 * process,
 * and resumes downloads from a previously saved state.
 * Tuần 6: Added state management (IDLE, RUNNING, PAUSED, FINISHED) for
 * pause/resume support
 */
public class Scheduler {

    // Tuần 6: State management
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
    private ExecutorService workerExecutor; // Tuần 6: Made non-final to support pause/resume
    private final PieceStorage pieceStorage;
    private final IStateStorage stateStorage;
    private final String localFilePath;
    private final String fileId;
    private final DownloadState downloadState;

    private volatile SchedulerState state = SchedulerState.IDLE; // Tuần 6: State tracking
    private final AtomicLong downloadedBytes = new AtomicLong(0);

    public Scheduler(ManifestModel manifest, DownloadClient downloadClient, ErrorCallback errorCallback,
            int numberOfWorkers,
            PieceStorage pieceStorage, IStateStorage stateStorage, String localFilePath, String fileId)
            throws IOException {
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
        // Note: This is an approximation; it doesn't account for the last piece being
        // smaller.
        // A more accurate way would be to sum the actual sizes of completed pieces.
        long completedPieceCount = this.downloadState.getCompletedPieceCount();
        this.downloadedBytes.set(completedPieceCount * manifest.getPieceSize());

        // Filter out pieces that are already completed
        this.pieceQueue = manifest.getPieces().stream()
                .filter(p -> !this.downloadState.isPieceCompleted(p.getId()))
                .collect(Collectors.toCollection(ConcurrentLinkedQueue::new));

        // Tuần 6: Don't initialize workerExecutor in constructor (will be created in
        // start())
        this.workerExecutor = null;
    }

    private void run() {
        System.out.println("Scheduler started for file of size: " + manifest.getFileSize());
        System.out.println("Total pieces to download: " + pieceQueue.size() + " (already completed: "
                + downloadState.getCompletedPieceCount() + ")");

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
                        String sourceIP = piece.getSources() != null && !piece.getSources().isEmpty()
                                ? extractIPFromUrl(piece.getSources().get(0))
                                : "unknown";
                        System.out.println(String.format(
                                "[%s] [DOWNLOAD] Worker %s downloading piece %d | Sources: %d | First source: %s",
                                java.time.LocalDateTime.now()
                                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                                Thread.currentThread().getName(), piece.getId(),
                                piece.getSources() != null ? piece.getSources().size() : 0,
                                sourceIP));

                        // Retry callback for hash mismatch
                        Consumer<PieceModel> retryCallback = (retryPiece) -> {
                            // Đưa piece trở lại queue với sources đã bỏ source đầu tiên
                            System.out
                                    .println(String.format("[%s] [RETRY] Re-queuing piece %d with %d remaining sources",
                                            java.time.LocalDateTime.now().format(
                                                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                                            retryPiece.getId(), retryPiece.getSources().size()));
                            pieceQueue.offer(retryPiece);
                        };

                        worker.download(piece, (data) -> {
                            long totalDownloaded = downloadedBytes.addAndGet(data.length);
                            String downloadedFrom = piece.getSources() != null && !piece.getSources().isEmpty()
                                    ? extractIPFromUrl(piece.getSources().get(0))
                                    : "unknown";
                            System.out.println(String.format(
                                    "[%s] [DOWNLOAD] ✓ Piece %d completed | FROM: %s | Size: %d KB | Total: %d MB",
                                    java.time.LocalDateTime.now()
                                            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                                    piece.getId(), downloadedFrom, data.length / 1024, totalDownloaded / 1024 / 1024));

                            // Track source progress (source đã được track trong
                            // SourceTrackingDownloadClient)
                            // Bytes sẽ được track trong DownloadTask khi piece download thành công
                        }, retryCallback);
                    }
                }
                // Check if the download finished naturally
                if (pieceQueue.isEmpty() && state == SchedulerState.RUNNING) {
                    state = SchedulerState.FINISHED;
                    System.out.println("All pieces downloaded. Download finished.");
                }
            });
        }
    }

    /**
     * Tuần 6: Pause the download. Workers will stop after finishing current piece.
     */
    public void pause() {
        if (state == SchedulerState.RUNNING) {
            System.out.println("Pausing scheduler...");
            state = SchedulerState.PAUSED;
            // This will stop workers from picking up new tasks because the loop condition
            // will fail.
            // It allows currently downloading pieces to finish.
            workerExecutor.shutdown(); // Does not accept new tasks.
            System.out.println("Scheduler paused. Workers will stop after finishing current piece.");
        }
    }

    /**
     * Tuần 6: Resume the download. Re-creates executor and continues downloading.
     */
    public void resume() {
        if (state == SchedulerState.PAUSED) {
            System.out.println("Resuming scheduler...");
            // Re-initialize the executor and submit workers to process the remaining queue
            run();
        }
    }

    /**
     * Tuần 6: Get the unique identifier for the file being downloaded.
     * 
     * @return The file ID
     */
    public String getFileId() {
        return fileId;
    }

    /**
     * Get the manifest model for this download
     * 
     * @return The manifest model
     */
    public ManifestModel getManifest() {
        return manifest;
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

    /**
     * Tuần 4: Download a specific piece on-demand (for VirtualFS)
     * This is called when VirtualFS detects a piece is needed but not yet
     * downloaded
     * 
     * @param pieceId The ID of the piece to download
     */
    public void downloadOnDemand(int pieceId) {
        // Check if piece is already completed
        synchronized (downloadState) {
            if (downloadState.isPieceCompleted(pieceId)) {
                return; // Already have it
            }
        }

        // Get the piece metadata
        if (pieceId < 0 || pieceId >= manifest.getPieces().size()) {
            System.err.println("Invalid piece ID: " + pieceId);
            return;
        }

        PieceModel piece = manifest.getPieces().get(pieceId);

        // Create a worker and download immediately (blocking for VirtualFS)
        DownloadWorker worker = new DownloadWorker(downloadClient, errorCallback, pieceStorage,
                stateStorage, localFilePath, fileId,
                manifest.getPieceSize(), downloadState);

        // Submit and wait for completion (blocking call for on-demand access)
        try {
            java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();

            long startTime = System.currentTimeMillis();
            System.out.println("Scheduler: Starting on-demand download for Piece " + pieceId);

            worker.download(piece, (data) -> {
                long totalDownloaded = downloadedBytes.addAndGet(data.length);
                long duration = System.currentTimeMillis() - startTime;
                System.out.println(
                        "On-demand downloaded piece " + pieceId + " in " + duration + "ms. Total: " + totalDownloaded);
                future.complete(null);
            });

            // Wait for download to complete (with timeout - reduced for better
            // responsiveness)
            future.get(10, java.util.concurrent.TimeUnit.SECONDS);

        } catch (java.util.concurrent.TimeoutException e) {
            System.err.println("Timeout downloading piece " + pieceId + " on-demand");
        } catch (java.util.concurrent.ExecutionException e) {
            System.err.println("Failed to download piece " + pieceId + " on-demand: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while downloading piece " + pieceId + " on-demand");
        }
    }

    public void shutdown() {
        workerExecutor.shutdownNow(); // Use shutdownNow to interrupt workers immediately
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

    public String getLocalFilePath() {
        return localFilePath;
    }

    public long getDownloadedBytes() {
        return downloadedBytes.get();
    }

    public boolean isPieceCompleted(int pieceId) {
        return downloadState.isPieceCompleted(pieceId);
    }
}
