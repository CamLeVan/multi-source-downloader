import com.fragmented.download.core.client.DownloadClient;
import com.fragmented.download.core.model.PieceModel;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * An implementation of the DownloadClient interface that uses OkHttp for network requests.
 * It includes a robust retry mechanism with exponential backoff for each source.
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
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "okhttp-retry-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public CompletableFuture<byte[]> downloadPiece(PieceModel piece) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        // Start the process by trying the first source. The logic will fallback to the next source if needed.
        tryPieceFromSource(piece, 0, future);
        return future;
    }

    /**
     * Attempts to download a piece from a specific source URL, identified by its index.
     * If this source fails permanently, it will proceed to the next source.
     */
    private void tryPieceFromSource(PieceModel piece, int sourceIndex, CompletableFuture<byte[]> future) {
        // If we've exhausted all available sources for this piece, the download fails.
        if (sourceIndex >= piece.getSources().size()) {
            future.completeExceptionally(new IOException("Failed to download piece " + piece.getId() + " from all available sources."));
            return;
        }

        String sourceUrl = piece.getSources().get(sourceIndex);
        long start = (long) piece.getId() * pieceSize;
        long end = start + pieceSize - 1;

        Request request = new Request.Builder()
                .url(sourceUrl)
                .header("Range", "bytes=" + start + "-" + end)
                .build();

        // Execute the request for the current source, starting with retry count 0.
        executeWithRetry(request, 0, piece, sourceIndex, future);
    }

    /**
     * Executes a request and handles the retry logic using a callback.
     */
    private void executeWithRetry(Request request, int retryCount, PieceModel piece, int sourceIndex, CompletableFuture<byte[]> future) {
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                // A network-level error occurred, attempt a retry.
                handleFailure(call, e, retryCount, piece, sourceIndex, future);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                // 5xx errors are server-side and might be transient, so we should retry.
                if (response.code() >= 500) {
                    handleFailure(call, new IOException("Server error: " + response.code()), retryCount, piece, sourceIndex, future);
                    response.close();
                    return;
                }

                // 2xx indicates success.
                if (response.isSuccessful()) {
                    try (ResponseBody body = response.body()) {
                        Objects.requireNonNull(body, "Response body is null");
                        future.complete(body.bytes());
                    } catch (IOException e) {
                        future.completeExceptionally(e);
                    }
                    return;
                }

                // Any other error (like a 4xx client error) is considered a permanent failure for this source.
                // We do not retry; we move directly to the next available source.
                System.err.println("Unrecoverable error for " + request.url() + ": " + response.code() + ". Trying next source.");
                response.close();
                tryPieceFromSource(piece, sourceIndex + 1, future);
            }
        });
    }

    /**
     * Handles a failure by either scheduling a retry with exponential backoff or moving to the next source.
     */
    private void handleFailure(Call call, IOException e, int retryCount, PieceModel piece, int sourceIndex, CompletableFuture<byte[]> future) {
        // Check if we still have retries left for the current source.
        if (retryCount < MAX_RETRIES) {
            long delayMs = (long) (Math.pow(2, retryCount) * INITIAL_BACKOFF_MS);
            System.err.println("Retrying piece " + piece.getId() + " from " + call.request().url() + " in " + delayMs + " ms. Attempt " + (retryCount + 1) + "/" + MAX_RETRIES + ". Error: " + e.getMessage());

            scheduler.schedule(() -> {
                executeWithRetry(call.request(), retryCount + 1, piece, sourceIndex, future);
            }, delayMs, TimeUnit.MILLISECONDS);
        } else {
            // No retries left for this source, so we move to the next one.
            System.err.println("Max retries reached for " + call.request().url() + ". Trying next source.");
            tryPieceFromSource(piece, sourceIndex + 1, future);
        }
    }

    /**
     * Shuts down the internal executor service. This should be called when the client is no longer needed.
     */
    public void shutdown() {
        this.scheduler.shutdownNow();
    }
}
