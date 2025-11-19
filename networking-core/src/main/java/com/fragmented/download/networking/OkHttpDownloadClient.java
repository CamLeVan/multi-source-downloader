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
    private final long fileSize;
    private final ScheduledExecutorService scheduler;

    public OkHttpDownloadClient(OkHttpClient httpClient, int numberOfThreads, long pieceSize, long fileSize) {
        this.httpClient = httpClient;
        this.pieceSize = pieceSize;
        this.fileSize = fileSize;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "okhttp-retry-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public CompletableFuture<byte[]> downloadPiece(PieceModel piece) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        // Bắt đầu thử từ nguồn đầu tiên (index 0)
        tryPieceFromSource(piece, 0, future);
        return future;
    }

    /**
     * Logic "Fallback": Thử tải từ một nguồn trong danh sách.
     * Nếu thất bại, nó sẽ gọi đệ quy chính nó với sourceIndex + 1.
     */
    private void tryPieceFromSource(PieceModel piece, int sourceIndex, CompletableFuture<byte[]> future) {

        // Nếu đã thử hết các nguồn (origin, mirror, peers)
        if (sourceIndex >= piece.getSources().size()) {
            future.completeExceptionally(new IOException("Failed to download piece " + piece.getId() + " from all available sources."));
            return;
        }

        String sourceUrl = piece.getSources().get(sourceIndex);
        long start = (long) piece.getId() * pieceSize;

        if (start >= fileSize) {
            future.completeExceptionally(new IOException("Invalid piece offset for piece " + piece.getId() + ". Start exceeds file size."));
            return;
        }

        long end = Math.min(start + pieceSize - 1, fileSize - 1);

        // Tạo request HTTP Range chính xác
        Request request = new Request.Builder()
                .url(sourceUrl)
                .header("Range", "bytes=" + start + "-" + end)
                .build();

        // Bắt đầu logic "Thử lại" (Retry) cho nguồn này
        executeWithRetry(request, 0, piece, sourceIndex, future);
    }

    /**
     * Thực thi request với logic "Thử lại" (Retry).
     */
    private void executeWithRetry(Request request, int retryCount, PieceModel piece, int sourceIndex, CompletableFuture<byte[]> future) {
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                // Lỗi mạng (ví dụ: Timeout, Connection refused) -> Thử lại
                handleFailure(call, e, retryCount, piece, sourceIndex, future);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {

                // *** BẮT ĐẦU SỬA LỖI ***
                // (Đã cập nhật logic onResponse)

                // 1. Thành công
                if (response.isSuccessful()) { // Mã 2xx
                    try (ResponseBody body = response.body()) {
                        Objects.requireNonNull(body, "Response body is null");
                        future.complete(body.bytes());
                    } catch (IOException e) {
                        future.completeExceptionally(e);
                    }
                    return; // Hoàn thành
                }

                // 2. Lỗi có thể "Thử lại" (Backoff)
                // (Lỗi Server 5xx, Timeout 408, Quá tải 429)
                if (response.code() >= 500 || response.code() == 408 || response.code() == 429) {
                    handleFailure(call, new IOException("Retryable HTTP Error: " + response.code()), retryCount, piece, sourceIndex, future);
                    response.close();
                    return;
                }

                // 3. Lỗi nghiêm trọng, "Thất bại" (Fail fast)
                // (Client gửi Range sai, không thể phục hồi)
                if (response.code() == 416) {
                    future.completeExceptionally(new IOException("Invalid Range requested (416). Failing piece " + piece.getId()));
                    response.close();
                    return;
                }

                // 4. Các lỗi 4xx khác -> "Bỏ qua" (Fallback)
                // (Ví dụ: 404 Peer chưa có mảnh, 403 Cấm)
                // Coi là nguồn này không hợp lệ -> Thử nguồn tiếp theo.
                System.err.println("Unrecoverable error for " + request.url() + ": " + response.code() + ". Trying next source.");
                response.close();
                tryPieceFromSource(piece, sourceIndex + 1, future);
                
                // *** KẾT THÚC SỬA LỖI ***
            }
        });
    }

    /**
     * Logic "Backoff": Xử lý khi thất bại, quyết định thử lại hoặc bỏ qua.
     */
    private void handleFailure(Call call, IOException e, int retryCount, PieceModel piece, int sourceIndex, CompletableFuture<byte[]> future) {

        if (retryCount < MAX_RETRIES) {
            // Tính toán thời gian chờ (1s, 2s, 4s, 8s, 16s)
            long delayMs = (long) (Math.pow(2, retryCount) * INITIAL_BACKOFF_MS);
            System.err.println("Retrying piece " + piece.getId() + " from " + call.request().url() + " in " + delayMs + " ms. Attempt " + (retryCount + 1) + "/" + MAX_RETRIES + ". Error: " + e.getMessage());

            // Lên lịch thử lại
            scheduler.schedule(() -> {
                executeWithRetry(call.request(), retryCount + 1, piece, sourceIndex, future);
            }, delayMs, TimeUnit.MILLISECONDS);
        } else {
            // Hết số lần thử lại cho nguồn này -> Thử nguồn tiếp theo (Fallback)
            System.err.println("Max retries reached for " + call.request().url() + ". Trying next source.");
            tryPieceFromSource(piece, sourceIndex + 1, future);
        }
    }

    /**
     * Tắt bộ lập lịch (scheduler) khi không cần dùng nữa.
     */
    public void shutdown() {
        this.scheduler.shutdownNow();
    }
}