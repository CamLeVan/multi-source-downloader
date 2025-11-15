package com.fragmented.download.networking;


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
        tryPieceFromSource(piece, 0, future);
        return future;
    }

 
    private void tryPieceFromSource(PieceModel piece, int sourceIndex, CompletableFuture<byte[]> future) {

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


        executeWithRetry(request, 0, piece, sourceIndex, future);
    }

    private void executeWithRetry(Request request, int retryCount, PieceModel piece, int sourceIndex, CompletableFuture<byte[]> future) {
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                handleFailure(call, e, retryCount, piece, sourceIndex, future);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
        
                if (response.code() >= 500) {
                    handleFailure(call, new IOException("Server error: " + response.code()), retryCount, piece, sourceIndex, future);
                    response.close();
                    return;
                }

                if (response.isSuccessful()) {
                    try (ResponseBody body = response.body()) {
                        Objects.requireNonNull(body, "Response body is null");
                        future.complete(body.bytes());
                    } catch (IOException e) {
                        future.completeExceptionally(e);
                    }
                    return;
                }


                System.err.println("Unrecoverable error for " + request.url() + ": " + response.code() + ". Trying next source.");
                response.close();
                tryPieceFromSource(piece, sourceIndex + 1, future);
            }
        });
    }


    private void handleFailure(Call call, IOException e, int retryCount, PieceModel piece, int sourceIndex, CompletableFuture<byte[]> future) {

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
