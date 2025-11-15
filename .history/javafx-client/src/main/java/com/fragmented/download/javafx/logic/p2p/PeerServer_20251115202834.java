package com.fragmented.download.javafx.logic.p2p;

import com.fragmented.download.core.model.DownloadState; // *** ĐÃ THAY ĐỔI ***
import com.fragmented.download.core.model.ManifestModel;
// import com.fragmented.download.core.storage.IStateStorage; // *** ĐÃ XÓA ***
import com.fragmented.download.core.storage.PieceStorage;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PeerServer.class);
    public static final int DEFAULT_PORT = 6881;

    private final HttpServer server;
    private final ExecutorService serverExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "peer-server-thread"));

    /**
     * *** ĐÃ SỬA LỖI ***
     * Constructor đã được cập nhật để nhận DownloadState (in-memory)
     * thay vì IStateStorage (disk I/O).
     */
    public PeerServer(DownloadState downloadState, PieceStorage pieceStorage, ManifestModel manifest, String fileId, String localFilePath) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(DEFAULT_PORT), 0);
        // The context is /piece, the handler will parse the rest of the URI
        
        // *** ĐÃ SỬA LỖI ***
        // Truyền đối tượng DownloadState (in-memory) vào PieceHandler
        this.server.createContext("/piece", new PieceHandler(downloadState, pieceStorage, manifest, fileId, localFilePath));
        this.server.setExecutor(Executors.newCachedThreadPool()); // Handle multiple requests concurrently
    }

    public void start() {
        serverExecutor.submit(() -> {
            log.info("PeerServer starting on port {}", DEFAULT_PORT);
            server.start();
            log.info("PeerServer started successfully.");
        });
    }

    @Override
    public void close() {
        log.info("Stopping PeerServer...");
        server.stop(1); // Stop with a 1-second delay for existing exchanges
        serverExecutor.shutdownNow();
        log.info("PeerServer stopped.");
    }
}