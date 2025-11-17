package com.fragmented.download.javafx;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import com.fragmented.download.core.client.ErrorCallback;
import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.core.storage.PieceStorage;
import com.fragmented.download.javafx.controller.DashboardController;
import com.fragmented.download.javafx.logic.Scheduler;
import com.fragmented.download.javafx.logic.SourceTrackingDownloadClient;
import com.fragmented.download.javafx.logic.p2p.PeerServer;
import com.fragmented.download.javafx.logic.p2p.TrackerClient;
import com.fragmented.download.javafx.logic.storage.JsonStateStorage;
import com.fragmented.download.javafx.logic.storage.SparseFileStorage;
import com.fragmented.download.javafx.logic.vfs.VirtualDownloaderFS;
import com.fragmented.download.javafx.model.DownloadTask;
import com.fragmented.download.javafx.util.ConfigManager;
import com.fragmented.download.javafx.util.FlowLogger;
import com.fragmented.download.javafx.util.NetworkUtil;
import com.fragmented.download.networking.OkHttpDownloadClient;
import com.google.gson.Gson;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Tuần 6: MainApp với multi-download support
 */
public class MainApp extends Application {

    private static final String VFS_MOUNT_DIR = "downloader-vfs";
    
    // Load config từ file
    private static final String TRACKER_URL = ConfigManager.get("tracker.url", "http://localhost:8081");
    private static final String ORIGIN_SERVER_URL = ConfigManager.get("origin.server.url", "https://localhost:8443");
    private static final String CLIENT_NAME = ConfigManager.get("client.name", "Client-1");
    private static final String MANIFEST_URL = ORIGIN_SERVER_URL + "/manifest/100MB.zip";
    
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObservableList<DownloadTask> downloadTasks = FXCollections.observableArrayList(); // Tuần 6: Multi-download
    private PeerServer peerServer;
    private VirtualDownloaderFS vfs;
    private TrackerClient trackerClient;
    private final String peerId = UUID.randomUUID().toString(); // Unique peer ID
    private String localIP;
    private int peerPort;

    @Override
    public void start(Stage primaryStage) throws IOException {
        // Get local IP address
        localIP = NetworkUtil.getLocalIPAddress();
        String hostname = NetworkUtil.getHostname();
        
        FlowLogger.logSection("CLIENT INITIALIZATION");
        FlowLogger.logInfo("Client Name: " + CLIENT_NAME, localIP);
        FlowLogger.logInfo("Hostname: " + hostname, localIP);
        FlowLogger.logInfo("Local IP Address: " + localIP, localIP);
        FlowLogger.logInfo("Tracker URL: " + TRACKER_URL, localIP);
        FlowLogger.logInfo("Origin Server URL: " + ORIGIN_SERVER_URL, localIP);
        FlowLogger.logSeparator();
        
        // Tuần 6: Set up the UI first
        URL fxmlLocation = getClass().getResource("/fxml/Dashboard.fxml");
        if (fxmlLocation == null) {
            throw new IOException("Cannot find FXML file. Make sure it's in the resources/fxml directory.");
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();
        
        DashboardController controller = loader.getController();
        controller.setMainApp(this);
        controller.setDownloadTasks(downloadTasks);

        primaryStage.setTitle("Multi-Source Downloader - " + CLIENT_NAME + " (" + localIP + ")");
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();

        // Start the initial download
        startDownload(MANIFEST_URL);
    }

    /**
     * Tuần 6: Start a new download and add it to the queue
     * Can be called multiple times for multi-download support
     */
    public void startDownload(String manifestUrl) {
        FlowLogger.logSection("DOWNLOAD FLOW STARTED");
        int step = 1;
        
        try {
            // 1. Fetch the manifest from the server
            FlowLogger.logStep(step++, "Fetching manifest", localIP, extractIPFromUrl(ORIGIN_SERVER_URL), 8443);
            ManifestModel manifest = fetchManifest(manifestUrl);
            FlowLogger.logInfo("Manifest received: " + manifest.getPieces().size() + " pieces, " + 
                (manifest.getFileSize() / 1024 / 1024) + " MB", localIP);

            // 2. Define file identifiers and paths
            String fileId = manifestUrl.substring(manifestUrl.lastIndexOf('/') + 1);
            String localFilePath = Paths.get(System.getProperty("user.home"), "Downloads", fileId).toString();
            FlowLogger.logInfo("File ID: " + fileId + ", Local path: " + localFilePath, localIP);

            // 3. Initialize TrackerClient (only once)
            if (trackerClient == null) {
                FlowLogger.logStep(step++, "Initializing Tracker Client", localIP, extractIPFromUrl(TRACKER_URL), 8081);
                trackerClient = new TrackerClient(httpClient, TRACKER_URL);
            }

            // 4. Set up storage components
            FlowLogger.logStep(step++, "Setting up storage", localIP, null, 0);
            IStateStorage stateStorage = new JsonStateStorage();
            PieceStorage pieceStorage = new SparseFileStorage();

            // Ensure the target file is created (as a sparse file)
            pieceStorage.createSparseFile(localFilePath, manifest.getFileSize());
            FlowLogger.logInfo("Sparse file created: " + localFilePath, localIP);

            // 5. Create and start the PeerServer (only once)
            if (peerServer == null) {
                try {
                    FlowLogger.logStep(step++, "Starting Peer Server", localIP, null, 0);
                    peerServer = new PeerServer(stateStorage, pieceStorage, manifest, fileId, localFilePath);
                    peerServer.start();
                    
                    peerPort = peerServer.getActualPort();
                    FlowLogger.logInfo("Peer Server started on " + localIP + ":" + peerPort, localIP);
                    
                    // Announce với tracker sau khi PeerServer đã start
                    FlowLogger.logStep(step++, "Announcing to Tracker", localIP + ":" + peerPort, extractIPFromUrl(TRACKER_URL), 8081);
                    boolean announced = trackerClient.announce(fileId, peerId, peerPort);
                    if (announced) {
                        FlowLogger.logInfo("Successfully announced to tracker: fileId=" + fileId + ", peer=" + localIP + ":" + peerPort, localIP);
                    } else {
                        FlowLogger.logError("Failed to announce to tracker", localIP, "Connection failed");
                    }
                } catch (IOException e) {
                    FlowLogger.logError("Failed to start PeerServer", localIP, e.getMessage());
                    System.err.println("P2P sharing will be disabled.");
                    e.printStackTrace();
                }
            }

            // 6. Enrich manifest với peer sources từ tracker
            FlowLogger.logStep(step++, "Fetching peer list from Tracker", localIP, extractIPFromUrl(TRACKER_URL), 8081);
            enrichManifestWithPeers(manifest, fileId);

            // 7. Set up backend components
            FlowLogger.logStep(step++, "Setting up Download Client", localIP, null, 0);
            OkHttpDownloadClient okHttpClient = new OkHttpDownloadClient(httpClient, 4, manifest.getPieceSize());
            SourceTrackingDownloadClient downloadClient = new SourceTrackingDownloadClient(okHttpClient);
            
            ErrorCallback errorCallback = (piece, cause) -> {
                FlowLogger.logError("Download failed for piece " + piece.getId(), localIP, cause.getMessage());
                System.err.println("FATAL: Download failed for piece " + piece.getId());
                cause.printStackTrace();
            };

            // 8. Create the scheduler
            FlowLogger.logStep(step++, "Creating Scheduler", localIP, null, 0);
            Scheduler scheduler = new Scheduler(manifest, downloadClient, errorCallback, 4, pieceStorage, stateStorage, localFilePath, fileId);

            // 9. Mount the Virtual Filesystem (only once)
            FlowLogger.logStep(step++, "Mounting Virtual Filesystem", localIP, null, 0);
            mountVFS(fileId, manifest, scheduler, pieceStorage, stateStorage, localFilePath);

            // 10. Create and add the download task to the list
            DownloadTask task = new DownloadTask(fileId, scheduler);
            downloadTasks.add(task);
            
            // 11. Setup source tracking callback
            downloadClient.setSourceTracker((sourceUrl) -> {
                // Track bytes downloaded from this source
                String sourceIP = extractIPFromUrl(sourceUrl);
                FlowLogger.logInfo("Piece downloaded from: " + sourceUrl, sourceIP);
                task.trackSourceBytes(sourceUrl, manifest.getPieceSize());
            });

            // 12. Start the download
            FlowLogger.logStep(step++, "Starting Download", localIP, null, 0);
            FlowLogger.logSeparator();
            scheduler.start();

            // 13. Periodically refresh peer list (background thread)
            startPeerRefreshThread(fileId);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Thêm peer sources vào manifest từ tracker
     */
    private void enrichManifestWithPeers(ManifestModel manifest, String fileId) {
        if (trackerClient == null) {
            return;
        }

        Set<String> peers = trackerClient.getPeers(fileId);
        if (peers.isEmpty()) {
            FlowLogger.logInfo("No peers found for fileId: " + fileId, localIP);
            return;
        }

        FlowLogger.logInfo("Found " + peers.size() + " peers for fileId: " + fileId, localIP);
        for (String peer : peers) {
            FlowLogger.logInfo("  - Peer: " + peer, peer.split(":")[0]);
        }
        
        // Thêm peer URLs vào sources của mỗi piece
        int peerCount = 0;
        for (PieceModel piece : manifest.getPieces()) {
            List<String> sources = new ArrayList<>(piece.getSources());
            
            // Thêm peer URLs vào sources (format: http://ip:port/piece/fileId/pieceId)
            for (String peerAddress : peers) {
                // Tránh thêm chính mình
                if (peerServer != null) {
                    String myAddress = localIP + ":" + peerPort;
                    if (peerAddress.contains(localIP) && peerAddress.contains(String.valueOf(peerPort))) {
                        continue;
                    }
                }
                
                // Tạo peer URL cho piece này
                String peerUrl = "http://" + peerAddress + "/piece/" + fileId + "/" + piece.getId();
                if (!sources.contains(peerUrl)) {
                    sources.add(peerUrl);
                    peerCount++;
                }
            }
            
            piece.setSources(sources);
        }
        
        FlowLogger.logInfo("Enriched manifest: Added " + peerCount + " peer sources across all pieces", localIP);
    }
    
    /**
     * Extract IP từ URL
     */
    private String extractIPFromUrl(String url) {
        try {
            if (url.startsWith("http://")) {
                url = url.substring(7);
            } else if (url.startsWith("https://")) {
                url = url.substring(8);
            }
            int colonIndex = url.indexOf(':');
            if (colonIndex > 0) {
                return url.substring(0, colonIndex);
            }
            int slashIndex = url.indexOf('/');
            if (slashIndex > 0) {
                return url.substring(0, slashIndex);
            }
            return url;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Background thread để refresh peer list định kỳ
     */
    private void startPeerRefreshThread(String fileId) {
        Thread refreshThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(30000); // Refresh mỗi 30 giây
                    
                    // Re-announce
                    if (peerServer != null && trackerClient != null) {
                        trackerClient.announce(fileId, peerId, peerServer.getActualPort());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        refreshThread.setDaemon(true);
        refreshThread.setName("peer-refresh-thread");
        refreshThread.start();
    }

    private void mountVFS(String fileId, ManifestModel manifest, Scheduler scheduler, PieceStorage pieceStorage, IStateStorage stateStorage, String localFilePath) {
        if (vfs == null) {
            new Thread(() -> {
                try {
                    VirtualDownloaderFS virtualDownloaderFS = new VirtualDownloaderFS(
                        manifest, scheduler, pieceStorage, stateStorage, localFilePath, fileId
                    );
                    
                    Path mountPoint = Paths.get(System.getProperty("user.home"), VFS_MOUNT_DIR);
                    if (!Files.exists(mountPoint)) {
                        Files.createDirectories(mountPoint);
                    }
                    System.out.println("Attempting to mount VFS at: " + mountPoint);

                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        System.err.println("WARNING: VirtualFS not supported on Windows.");
                        System.err.println("Application will continue without VirtualFS.");
                    } else {
                        System.out.println("Running on macOS/Linux, using FUSE to mount.");
                        this.vfs = virtualDownloaderFS;
                        this.vfs.mount(mountPoint, true, false);
                    }
                } catch (Exception e) {
                    System.err.println("FATAL: Failed to mount virtual filesystem. The application will continue without it.");
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private ManifestModel fetchManifest(String manifestUrl) throws IOException {
        Request request = new Request.Builder().url(manifestUrl).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download manifest: " + response);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Manifest response body is null");
            }
            try (Reader reader = new InputStreamReader(body.byteStream())) {
                return new Gson().fromJson(reader, ManifestModel.class);
            }
        }
    }

    @Override
    public void stop() {
        System.out.println("Unmounting virtual filesystem...");
        try {
            if (vfs != null) {
                vfs.umount();
            }
        } catch (Throwable e) {
            System.err.println("Error while unmounting VFS: " + e.getMessage());
        }
        System.out.println("Shutting down application...");
        if (peerServer != null) {
            peerServer.close();
        }

        // Tuần 6: Pause all downloads before shutdown
        downloadTasks.forEach(task -> task.getScheduler().pause());
        
        System.out.println("Shutdown complete.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
