package com.fragmented.download.javafx.logic.p2p;

import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.core.storage.IStateStorage;
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
    private static final int MAX_PORT_ATTEMPTS = 10; // Try 10 ports (6881-6890)

    private final HttpServer server;
    private final int actualPort; // Port actually used (may differ from DEFAULT_PORT if conflict)
    private final ExecutorService serverExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "peer-server-thread"));
    
    // Flag để track server đã sẵn sàng chưa
    private volatile boolean isReady = false;
    private final Object readyLock = new Object();

    public PeerServer(IStateStorage stateStorage, PieceStorage pieceStorage, ManifestModel manifest, String fileId, String localFilePath) throws IOException {
        // Improvement: Port conflict handling - try multiple ports if DEFAULT_PORT is busy
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
                    // Last attempt failed, throw exception
                    throw new IOException("Failed to create PeerServer: Could not bind to any port in range " + 
                                        DEFAULT_PORT + "-" + (DEFAULT_PORT + MAX_PORT_ATTEMPTS - 1), e);
                }
                // Try next port
                log.debug("Port {} is busy, trying next port...", portToTry);
            }
        }
        
        this.server = createdServer;
        this.actualPort = portUsed;
        
        // The context is /piece, the handler will parse the rest of the URI
        this.server.createContext("/piece", new PieceHandler(stateStorage, pieceStorage, manifest, fileId, localFilePath));
        
        // Improvement: Health check endpoint for monitoring and debugging
        this.server.createContext("/health", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
            } catch (IOException e) {
                log.error("Error handling health check", e);
            }
        });
        
        this.server.setExecutor(Executors.newCachedThreadPool()); // Handle multiple requests concurrently
    }
    
    /**
     * Get the actual port the server is listening on.
     * May differ from DEFAULT_PORT if there was a port conflict.
     * @return The port number
     */
    public int getActualPort() {
        return actualPort;
    }

    public void start() {
        serverExecutor.submit(() -> {
            log.info("PeerServer starting on port {}", actualPort);
            server.start();
            synchronized (readyLock) {
                isReady = true;
                readyLock.notifyAll();
            }
            log.info("PeerServer started successfully on port {}", actualPort);
        });
    }
    
    /**
     * Đợi server sẵn sàng (timeout sau 5 giây)
     * @return true nếu server đã sẵn sàng, false nếu timeout
     */
    public boolean waitUntilReady(long timeoutMs) {
        synchronized (readyLock) {
            if (isReady) {
                return true;
            }
            try {
                readyLock.wait(timeoutMs);
                return isReady;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
    
    /**
     * Kiểm tra server đã sẵn sàng chưa
     */
    public boolean isReady() {
        return isReady;
    }

    @Override
    public void close() {
        log.info("Stopping PeerServer...");
        server.stop(1); // Stop with a 1-second delay for existing exchanges
        serverExecutor.shutdownNow();
        log.info("PeerServer stopped.");
    }
}
