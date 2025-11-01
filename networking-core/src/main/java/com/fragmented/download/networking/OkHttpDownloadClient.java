import com.fragmented.download.core.client.DownloadClient;
import com.fragmented.download.core.model.PieceModel;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * An implementation of the DownloadClient interface that uses OkHttp for network requests.
 * It includes a robust retry mechanism with exponential backoff.
 */
public class OkHttpDownloadClient implements DownloadClient {

    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private final OkHttpClient httpClient;
    private final long pieceSize;
    private final ScheduledExecutorService scheduler;

    public OkHttpDownloadClient(OkHttpClient httpClient, int numberOfThreads, long pieceSize) {
        this.httpClient = httpClient;
        this.pieceSize = pieceSize;
        // A single-threaded scheduler is sufficient for managing retry tasks.
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "okhttp-retry-scheduler"));
    }

    @Override
    public CompletableFuture<byte[]> downloadPiece(PieceModel piece) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        // Start the first download attempt cycle without any delay.
        attemptDownloadCycle(piece, 0, future);
        return future;
    }

    /**
     * Represents a full attempt to download a piece by trying all its sources.
     * If this attempt is a retry, it's scheduled with an exponential backoff delay.
     */
    private void attemptDownloadCycle(PieceModel piece, int retryCount, CompletableFuture<byte[]> future) {
        if (retryCount > 0) {
            long delayMs = (long) (Math.pow(2, retryCount - 1) * INITIAL_BACKOFF_MS);
            System.out.println("Scheduling retry " + retryCount + " for piece " + piece.getId() + " after " + delayMs + " ms.");
            scheduler.schedule(() -> tryAllSources(piece, 0, retryCount, future), delayMs, TimeUnit.MILLISECONDS);
        } else {
            // First attempt, run immediately.
            tryAllSources(piece, 0, retryCount, future);
        }
    }

    /**
     * Recursively tries to download a piece from the list of available sources.
     * If all sources fail, it triggers the next retry cycle.
     */
    private void tryAllSources(PieceModel piece, int sourceIndex, int retryCount, CompletableFuture<byte[]> future) {
        // Base case: If we've exhausted all sources for this piece.
        if (sourceIndex >= piece.getSources().size()) {
            // Check if we can still retry.
            if (retryCount < MAX_RETRIES) {
                // Move to the next retry cycle.
                attemptDownloadCycle(piece, retryCount + 1, future);
            } else {
                // All retries exhausted, fail the download permanently.
                String errorMessage = "Failed to download piece " + piece.getId() + " from all sources after " + MAX_RETRIES + " retries.";
                System.err.println(errorMessage);
                future.completeExceptionally(new IOException(errorMessage));
            }
            return;
        }

        String sourceUrl = piece.getSources().get(sourceIndex);
        long start = (long) piece.getId() * pieceSize;
        long end = start + pieceSize - 1;

        Request request = new Request.Builder()
                .url(sourceUrl)
                .header("Range", "bytes=" + start + "-" + end)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                System.err.println("Failed to download piece " + piece.getId() + " from " + sourceUrl + ": " + e.getMessage());
                // Try the next source.
                tryAllSources(piece, sourceIndex + 1, retryCount, future);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    // Success is 2xx or specifically 206 Partial Content.
                    if (response.isSuccessful()) {
                        Objects.requireNonNull(body, "Response body is null");
                        System.out.println("Successfully downloaded piece " + piece.getId() + " from " + sourceUrl);
                        future.complete(body.bytes());
                    } else {
                        // For 4xx or 5xx errors, we treat this source as failed and try the next one.
                        System.err.println("Failed to download piece " + piece.getId() + " from " + sourceUrl + ". Status: " + response.code());
                        tryAllSources(piece, sourceIndex + 1, retryCount, future);
                    }
                }
            }
        });
    }

    /**
     * Shuts down the internal executor services. This should be called when the client is no longer needed.
     */
    public void shutdown() {
        this.scheduler.shutdownNow();
    }
}
