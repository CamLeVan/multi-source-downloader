package com.fragmented.download.javafx.logic.p2p;

import com.fragmented.download.core.model.DownloadState; // *** ĐÃ THÊM ***
import com.fragmented.download.core.model.ManifestModel;
// import com.fragmented.download.core.storage.IStateStorage; // *** ĐÃ XÓA ***
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

    // private final IStateStorage stateStorage; // *** ĐÃ XÓA ***
    private final DownloadState downloadState; // *** ĐÃ THÊM ***
    private final PieceStorage pieceStorage;
    private final ManifestModel manifest;
    private final String fileId;
    private final String localFilePath;

    /**
     * *** ĐÃ SỬA LỖI ***
     * Constructor đã được cập nhật để nhận DownloadState (in-memory).
     */
    public PieceHandler(DownloadState downloadState, PieceStorage pieceStorage, ManifestModel manifest, String fileId, String localFilePath) {
        // this.stateStorage = stateStorage; // *** ĐÃ XÓA ***
        this.downloadState = downloadState; // *** ĐÃ THÊM ***
        this.pieceStorage = pieceStorage;
        this.manifest = manifest;
        this.fileId = fileId;
        this.localFilePath = localFilePath;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        URI uri = exchange.getRequestURI();
        log.debug("Received request: {}", uri);

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
            log.warn("Request for mismatched fileId. Expected: {}, Got: {}", this.fileId, reqFileId);
            sendResponse(exchange, 404, "Not Found: File ID mismatch.");
            return;
        }

        try {
            // *** ĐÃ SỬA LỖI ***
            // Không đọc từ ổ đĩa (stateStorage.loadState).
            // Đọc trực tiếp từ đối tượng DownloadState (in-memory) được chia sẻ.
            if (this.downloadState != null && this.downloadState.isPieceCompleted(pieceIndex)) {
                long pieceSize = manifest.getPieceSize();
                long offset = (long) pieceIndex * pieceSize;
                // Tính toán độ dài mảnh cuối (có thể nhỏ hơn)
                long length = Math.min(pieceSize, manifest.getFileSize() - offset);

                log.info("Serving piece {} for file {}", pieceIndex, this.fileId);
                byte[] data = pieceStorage.readPiece(this.localFilePath, offset, (int) length);

                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
            } else {
                // Trả về 404 nếu chúng ta chưa có mảnh đó (đúng chuẩn P2P)
                log.warn("Piece {} for file {} not available for serving.", pieceIndex, this.fileId);
                sendResponse(exchange, 404, "Not Found: Piece not available.");
            }
        } catch (IOException e) {
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