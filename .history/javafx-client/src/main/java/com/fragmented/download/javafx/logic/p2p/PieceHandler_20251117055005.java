package com.fragmented.download.javafx.logic.p2p;

import com.fragmented.download.core.model.DownloadState;
import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.core.storage.PieceStorage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

public class PieceHandler implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(PieceHandler.class);

    private final IStateStorage stateStorage;
    private final PieceStorage pieceStorage;
    private final ManifestModel manifest;
    private final String fileId;
    private final String localFilePath;

    public PieceHandler(IStateStorage stateStorage, PieceStorage pieceStorage, ManifestModel manifest, String fileId, String localFilePath) {
        this.stateStorage = stateStorage;
        this.pieceStorage = pieceStorage;
        this.manifest = manifest;
        this.fileId = fileId;
        this.localFilePath = localFilePath;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        String peerIP = exchange.getRemoteAddress().getAddress().getHostAddress();
        int peerPort = exchange.getRemoteAddress().getPort();
        String peerAddress = peerIP + ":" + peerPort;
        
        log.debug("Received request: {} from {}", uri, peerAddress);

        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        String[] path = uri.getPath().split("/");
        // Expected: /piece/{fileId}/{pieceIndex} -> parts are "", "piece", "{fileId}", "{pieceIndex}"
        if (path.length != 4) {
            sendResponse(exchange, 400, "Bad Request: Invalid URI format.");
            return;
        }

        String reqFileId = path[2];
        int pieceIndex;
        try {
            pieceIndex = Integer.parseInt(path[3]);
        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, "Bad Request: Invalid piece index.");
            return;
        }

        if (!this.fileId.equals(reqFileId)) {
            System.err.println(String.format("[%s] [PEER] ✗ File ID mismatch | FROM: %s → TO: Peer | Expected: %s, Got: %s", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                peerAddress, this.fileId, reqFileId));
            log.warn("Request for mismatched fileId. Expected: {}, Got: {}", this.fileId, reqFileId);
            sendResponse(exchange, 404, "Not Found: File ID mismatch.");
            return;
        }

        try {
            DownloadState state = stateStorage.loadState(this.fileId);
            if (state != null && state.isPieceCompleted(pieceIndex)) {
                long pieceSize = manifest.getPieceSize();
                long offset = (long) pieceIndex * pieceSize;
                long length = Math.min(pieceSize, manifest.getFileSize() - offset);

                System.out.println(String.format("[%s] [PEER] GET /piece/%s/%d | FROM: %s → TO: Peer", 
                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                    reqFileId, pieceIndex, peerAddress));
                
                log.info("Serving piece {} for file {}", pieceIndex, this.fileId);
                byte[] data = pieceStorage.readPiece(this.localFilePath, offset, (int) length);

                // Improvement: Set Content-Type header for binary data (best practice)
                exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
                
                System.out.println(String.format("[%s] [PEER] ✓ Piece %d sent | FROM: Peer → TO: %s | Size: %d KB", 
                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                    pieceIndex, peerAddress, data.length / 1024));
            } else {
                System.err.println(String.format("[%s] [PEER] ✗ Piece %d not available | FROM: %s | Reason: Not downloaded yet", 
                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                    pieceIndex, peerAddress));
                log.warn("Piece {} for file {} not available for serving.", pieceIndex, this.fileId);
                sendResponse(exchange, 404, "Not Found: Piece not available.");
            }
        } catch (IOException e) {
            System.err.println(String.format("[%s] [PEER] ✗ Error serving piece %d | FROM: %s | Error: %s", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                pieceIndex, peerAddress, e.getMessage()));
            log.error("Error processing piece request for file {} piece {}", this.fileId, pieceIndex, e);
            sendResponse(exchange, 500, "Internal Server Error");
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] response = message.getBytes("UTF-8");
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}
