package com.fragmented.download.javafx.logic.streaming;

import com.fragmented.download.core.model.DownloadState;
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
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final ExecutorService serverExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "streaming-server-thread"));
    
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
                    throw new IOException("Failed to create LocalStreamingServer: Could not bind to any port in range " + 
                                        DEFAULT_PORT + "-" + (DEFAULT_PORT + MAX_PORT_ATTEMPTS - 1), e);
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
                
                // Ensure valid range
                if (start < 0) start = 0;
                if (end >= manifest.getFileSize()) end = manifest.getFileSize() - 1;
                if (start > end) {
                    sendError(exchange, 416, "Range Not Satisfiable");
                    return;
                }
                
                long contentLength = end - start + 1;
                
                // Calculate which pieces are needed
                long pieceSize = manifest.getPieceSize();
                int startPieceId = (int) (start / pieceSize);
                int endPieceId = (int) (end / pieceSize);
                
                // Trigger on-demand download for needed pieces
                DownloadState state = stateStorage.loadState(fileId);
                if (state == null) {
                    sendError(exchange, 500, "Internal Server Error: State not found");
                    return;
                }
                
                // Download missing pieces on-demand
                for (int pieceId = startPieceId; pieceId <= endPieceId; pieceId++) {
                    if (!state.isPieceCompleted(pieceId)) {
                        log.info("Streaming: Piece {} not available, downloading on-demand...", pieceId);
                        scheduler.downloadOnDemand(pieceId);
                        
                        // Re-check state
                        state = stateStorage.loadState(fileId);
                        if (state == null || !state.isPieceCompleted(pieceId)) {
                            log.error("Streaming: Failed to download piece {} on-demand", pieceId);
                            sendError(exchange, 503, "Service Unavailable: Piece download failed");
                            return;
                        }
                    }
                }
                
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
                }
                
            } catch (Exception e) {
                log.error("Error handling streaming request", e);
                sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }
        
        private void streamFileData(OutputStream os, long start, long length) throws IOException {
            byte[] buffer = new byte[8192]; // 8KB buffer
            long remaining = length;
            long currentOffset = start;
            
            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                byte[] data = pieceStorage.readPiece(localFilePath, currentOffset, toRead);
                
                if (data == null || data.length == 0) {
                    break; // No more data available
                }
                
                os.write(data, 0, Math.min(data.length, toRead));
                currentOffset += data.length;
                remaining -= data.length;
            }
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

