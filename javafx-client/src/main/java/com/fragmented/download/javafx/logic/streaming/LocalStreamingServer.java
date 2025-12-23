package com.fragmented.download.javafx.logic.streaming;

import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.core.storage.PieceStorage;
import com.fragmented.download.javafx.logic.Scheduler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local HTTP Streaming Server
 * Cho phép truy cập file đang tải qua HTTP (hoạt động trên mọi OS)
 * 
 * Features:
 * - HTTP Range request support (cho video/audio streaming)
 * - On-demand download khi có request
 * - Hoạt động trên Windows, macOS, Linux
 */
public class LocalStreamingServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LocalStreamingServer.class);
    private static final int DEFAULT_PORT = 8888;
    private static final int MAX_PORT_ATTEMPTS = 10; // Try 10 ports (8888-8897)

    private final HttpServer server;
    private final int actualPort;

    // Executor cho Server chạy (để không block UI Main Thread)
    private final ExecutorService serverExecutor = Executors
            .newSingleThreadExecutor(r -> new Thread(r, "streaming-server-thread"));

    // Executor riêng cho việc đọc trước dữ liệu (Prefetching)
    private final ExecutorService prefetchExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "streaming-prefetch");
        t.setDaemon(true);
        return t;
    });

    private final ManifestModel manifest;
    private final Scheduler scheduler;
    private final PieceStorage pieceStorage;
    private final IStateStorage stateStorage;
    private final String localFilePath;
    private final String fileId;
    private final String fileName;

    public LocalStreamingServer(ManifestModel manifest, Scheduler scheduler,
            PieceStorage pieceStorage, IStateStorage stateStorage,
            String localFilePath, String fileId) throws IOException {
        this.manifest = manifest;
        this.scheduler = scheduler;
        this.pieceStorage = pieceStorage;
        this.stateStorage = stateStorage;
        this.localFilePath = localFilePath;
        this.fileId = fileId;
        this.fileName = Paths.get(localFilePath).getFileName().toString();

        // Try to create server on available port
        HttpServer createdServer = null;
        int portUsed = DEFAULT_PORT;

        for (int attempt = 0; attempt < MAX_PORT_ATTEMPTS; attempt++) {
            int portToTry = DEFAULT_PORT + attempt;
            try {
                createdServer = HttpServer.create(new InetSocketAddress(portToTry), 0);
                portUsed = portToTry;
                if (attempt > 0) {
                    log.warn("Port {} was busy, using port {} instead", DEFAULT_PORT, portUsed);
                }
                break;
            } catch (IOException e) {
                if (attempt == MAX_PORT_ATTEMPTS - 1) {
                    throw new IOException(
                            "Failed to create LocalStreamingServer: Could not bind to any port in range " +
                                    DEFAULT_PORT + "-" + (DEFAULT_PORT + MAX_PORT_ATTEMPTS - 1),
                            e);
                }
                log.debug("Port {} is busy, trying next port...", portToTry);
            }
        }

        this.server = createdServer;
        this.actualPort = portUsed;

        // Create context for file streaming
        this.server.createContext("/stream/" + fileName, new StreamingHandler());

        // Health check endpoint
        this.server.createContext("/health", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
            } catch (IOException e) {
                log.error("Error handling health check", e);
            }
        });

        this.server.setExecutor(Executors.newCachedThreadPool());
    }

    public int getActualPort() {
        return actualPort;
    }

    public String getStreamingUrl() {
        return "http://localhost:" + actualPort + "/stream/" + fileName;
    }

    /**
     * Khởi động server (Chạy trên thread riêng)
     */
    public void start() {
        serverExecutor.submit(() -> {
            log.info("LocalStreamingServer starting on port {}", actualPort);
            server.start();
            log.info("LocalStreamingServer started successfully on port {}", actualPort);
            System.out.println("📺 Streaming URL: " + getStreamingUrl());
        });
    }

    @Override
    public void close() {
        if (server != null) {
            log.info("Stopping LocalStreamingServer...");
            server.stop(0);
            log.info("LocalStreamingServer stopped");
        }
        serverExecutor.shutdown();
        prefetchExecutor.shutdown(); // Close the prefetch pool
    }

    /**
     * HTTP Handler để stream file với Range request support
     */
    private class StreamingHandler implements HttpHandler {
        private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d+)-(\\d*)");

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();

            if (!"GET".equals(method)) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                // Parse Range header (cho video/audio streaming)
                String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
                log.info("STREAM: Received REQ {} {} | Range: {}", method, exchange.getRequestURI(),
                        rangeHeader != null ? rangeHeader : "Full");

                long start = 0;
                long end = manifest.getFileSize() - 1;

                if (rangeHeader != null) {
                    Matcher matcher = RANGE_PATTERN.matcher(rangeHeader);
                    if (matcher.matches()) {
                        start = Long.parseLong(matcher.group(1));
                        if (matcher.group(2) != null && !matcher.group(2).isEmpty()) {
                            end = Long.parseLong(matcher.group(2));
                        }
                    }
                }

                log.info("STREAM: Calculated Range {}-{} (Len: {})", start, end, end - start + 1);

                // Ensure valid range
                if (start < 0)
                    start = 0;
                if (end >= manifest.getFileSize())
                    end = manifest.getFileSize() - 1;

                log.info("STREAM CALC: Requesting bytes {}-{} (Chunk size: {})", start, end, end - start + 1);

                if (start > end) {
                    log.warn("STREAM ERROR: Invalid Range {}-{}", start, end);
                    sendError(exchange, 416, "Range Not Satisfiable");
                    return;
                }

                long contentLength = end - start + 1;

                // Calculate which pieces are needed
                long pieceSize = manifest.getPieceSize();

                // Optimization: Pre-trigger download for critical MP4 Metadata (Start & End of
                // file) ASYNC
                // We run this in a separate thread to NOT block the HTTP Response (TTFB)
                int lastPieceIdx = (int) ((manifest.getFileSize() - 1) / pieceSize);
                prefetchExecutor.submit(() -> {
                    // Priority: Start (Header) -> End (MOOV) -> Second (Buffer)
                    scheduler.downloadOnDemand(0);
                    scheduler.downloadOnDemand(lastPieceIdx);
                    if (lastPieceIdx > 1)
                        scheduler.downloadOnDemand(lastPieceIdx - 1);
                    scheduler.downloadOnDemand(1);
                });

                // Send response headers
                exchange.getResponseHeaders().set("Content-Type", getContentType(fileName));
                exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(contentLength));

                if (rangeHeader != null) {
                    // Partial content response (206)
                    exchange.getResponseHeaders().set("Content-Range",
                            String.format("bytes %d-%d/%d", start, end, manifest.getFileSize()));
                    exchange.sendResponseHeaders(206, contentLength);
                } else {
                    // Full content response (200)
                    exchange.sendResponseHeaders(200, contentLength);
                }

                // Stream data
                try (OutputStream os = exchange.getResponseBody()) {
                    streamFileData(os, start, contentLength);
                } catch (IOException e) {
                    // Suppress expected errors during streaming (Client disconnects, etc.)
                    String msg = e.getMessage();
                    if (msg != null && (msg.contains("insufficient bytes written")
                            || msg.contains("An established connection was aborted")
                            || msg.contains("Broken pipe"))) {
                        log.debug("Streaming finished early (Client disconnected): " + msg);
                    } else {
                        throw e; // Rethrow unexpected IO errors
                    }
                }

            } catch (Throwable e) {
                // Check once more in case it bubbled up
                String msg = e.getMessage();
                if (msg != null && (msg.contains("insufficient bytes written") || msg.contains("Broken pipe"))) {
                    log.debug("Streaming stopped abruptly: " + msg);
                } else {
                    log.error("CRITICAL ERROR handling streaming request", e);
                    // Avoid sending error on closed connection
                    try {
                        exchange.close();
                    } catch (Throwable ignore) {
                    }
                }
            }
        }

        private void streamFileData(OutputStream os, long start, long length) throws IOException {
            long remaining = length;
            long currentPos = start;
            long pieceSize = manifest.getPieceSize();
            log.info("STREAM: Starting data transfer loop (Start: {}, Len: {}).", start, length);

            while (remaining > 0) {
                // 1. Identify Needed Piece
                int pieceId = (int) (currentPos / pieceSize);

                // Read-Ahead Optimization: Trigger download for NEXT pieces asynchronously
                int nextPiece = pieceId + 1;
                // Pre-fetch next 2 pieces to ensure smooth playback
                if (nextPiece < manifest.getPieces().size() && !scheduler.isPieceCompleted(nextPiece)) {
                    prefetchExecutor.submit(() -> {
                        scheduler.downloadOnDemand(nextPiece);
                        if (nextPiece + 1 < manifest.getPieces().size())
                            scheduler.downloadOnDemand(nextPiece + 1);
                    });
                }

                // 2. Ensure piece is available (Progressive Download Logic)
                int attempts = 0;
                // Wait up to 60 seconds (was 20s) for the piece to arrive
                // Increased timeout for slow networks
                while (!scheduler.isPieceCompleted(pieceId) && attempts < 120) {
                    if (attempts == 0 || attempts % 4 == 0) { // Retry every 2 seconds (500ms * 4)
                        log.info("Streaming: Waiting for Piece {} (Attempt {}/120)...", pieceId, attempts);
                        scheduler.downloadOnDemand(pieceId); // Trigger priority download aggressively
                    }

                    if (scheduler.isPieceCompleted(pieceId))
                        break;

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Streaming Interrupted");
                    }
                    attempts++;
                }

                if (!scheduler.isPieceCompleted(pieceId)) {
                    log.error("Streaming timeout: Piece {} unavailable after wait.", pieceId);
                    break; // Stop streaming
                }

                // 3. Read Data Chunk
                // Calculate bytes remaining in THIS piece to prevent reading into the next
                // unverified piece
                long nextPieceStart = (long) (pieceId + 1) * pieceSize;
                long bytesInThisPiece = nextPieceStart - currentPos;

                // Read in 512KB chunks (optimized for video streaming)
                int toRead = (int) Math.min(524288, remaining);
                toRead = (int) Math.min(toRead, bytesInThisPiece);

                // Ensure we don't read past the file size
                if (currentPos + toRead > manifest.getFileSize()) {
                    toRead = (int) (manifest.getFileSize() - currentPos);
                }

                if (toRead <= 0)
                    break;

                byte[] data = null;
                // Retry read logic for disk stability - Increased to 20 attempts
                for (int i = 0; i < 20; i++) {
                    try {
                        // log.debug("STREAM READ: Attempt {} read at offset {} len {}", i + 1,
                        // currentPos, toRead);
                        data = pieceStorage.readPiece(localFilePath, currentPos, toRead);
                        if (data != null && data.length > 0) {
                            if (i > 0)
                                log.info("STREAM: Read recovered after {} attempts at {}", i + 1, currentPos);
                            break;
                        }
                    } catch (IOException e) {
                        if (i % 5 == 0)
                            log.warn("STREAM: Disk Read retry {}/20 at {}: {}", i + 1, currentPos, e.getMessage());
                        // Transient disk error?
                        try {
                            Thread.sleep(50);
                        } catch (Exception ignore) {
                        }
                    }
                }

                if (data == null || data.length == 0) {
                    log.error("Streaming Error: Failed to read data at offset {}, len {}", currentPos, toRead);
                    break;
                }

                // 4. Send to Client
                try {
                    os.write(data);
                    os.flush();
                    // log.debug("STREAM SENT: {} bytes at offset {}", data.length, currentPos); //
                    // Enable for verbose debug
                } catch (IOException e) {
                    // Check for client disconnect
                    String msg = e.getMessage();
                    if (msg != null && (msg.contains("Aborted") || msg.contains("Pipe")
                            || msg.contains("Reset") || msg.contains("insufficient bytes"))) {
                        log.debug("Streaming client disconnected.");
                        break;
                    } else {
                        throw e; // Rethrow real errors
                    }
                }

                currentPos += data.length;
                remaining -= data.length;
            }
            log.info("STREAM DONE: Finished sending ranges. Remaining: {}", remaining);
        }

        private String getContentType(String fileName) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".mp4") || lower.endsWith(".m4v")) {
                return "video/mp4";
            } else if (lower.endsWith(".avi")) {
                return "video/x-msvideo";
            } else if (lower.endsWith(".mkv")) {
                return "video/x-matroska";
            } else if (lower.endsWith(".mov")) {
                return "video/quicktime";
            } else if (lower.endsWith(".mp3")) {
                return "audio/mpeg";
            } else if (lower.endsWith(".pdf")) {
                return "application/pdf";
            } else if (lower.endsWith(".zip")) {
                return "application/zip";
            }
            return "application/octet-stream";
        }

        private void sendError(HttpExchange exchange, int code, String message) throws IOException {
            exchange.sendResponseHeaders(code, message.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(message.getBytes());
            }
        }
    }
}
