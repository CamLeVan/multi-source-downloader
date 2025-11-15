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
                        worker.download(piece).whenComplete((data, throwable) -> {
                            if (throwable == null) {
                                long totalDownloaded = downloadedBytes.addAndGet(data.length);
                                System.out.println("Downloaded piece " + piece.getId() + ". Total downloaded: " + totalDownloaded);
                            }
                            // Error is already handled by the worker's errorCallback
                        });
                    }
                }
                System.out.println("Worker " + Thread.currentThread().getName() + " finished.");
            });
        }
    }

    /**
     * Downloads a single piece on-demand and returns a CompletableFuture that completes when the download is finished.
     * This is intended for blocking operations, like the VFS read.
     */
    public CompletableFuture<Void> download(PieceModel piece) {
        DownloadWorker worker = new DownloadWorker(downloadClient, errorCallback, pieceStorage, stateStorage,
                localFilePath, fileId, manifest.getPieceSize(), downloadState);
        
        // We use runAsync to avoid blocking the caller thread, but the logic inside will block
        // the thread from workerExecutor until the download is complete.
        return CompletableFuture.runAsync(() -> {
            System.out.println("On-demand download for piece " + piece.getId());
            // worker.download returns a CompletableFuture<byte[]>, .join() waits for it to complete.
            worker.download(piece).join(); 
        }, workerExecutor);
    }

    /**
     * Downloads a list of pieces on-demand and returns a CompletableFuture that completes when all are done.
     */
    public CompletableFuture<Void> downloadPiecesOnDemand(java.util.List<PieceModel> pieces) {
        java.util.List<CompletableFuture<Void>> futures = pieces.stream()
                .map(this::download)
                .collect(Collectors.toList());
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
        java.util.List<PieceModel> piecesToDownload = manifest.getPieces().subList(startIndex, endIndex + 1);
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
