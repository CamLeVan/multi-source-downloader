package com.fragmented.download.javafx;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fragmented.download.core.client.ErrorCallback;
import com.fragmented.download.core.model.DownloadState;
import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.core.model.PieceModel;
import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.core.storage.PieceStorage;
import com.fragmented.download.javafx.controller.DashboardController;
import com.fragmented.download.javafx.logic.Scheduler;
import com.fragmented.download.javafx.logic.SourceTrackingDownloadClient;
import com.fragmented.download.javafx.logic.p2p.PeerServer;
import com.fragmented.download.javafx.logic.p2p.TrackerClient;
import com.fragmented.download.javafx.logic.storage.JsonStateStorage;
import com.fragmented.download.javafx.logic.storage.SparseFileStorage;
import com.fragmented.download.javafx.logic.streaming.LocalStreamingServer;
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

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObservableList<DownloadTask> downloadTasks = FXCollections.observableArrayList(); // Tuần 6:
                                                                                                    // Multi-download
    private PeerServer peerServer;
    private VirtualDownloaderFS vfs;

    private TrackerClient trackerClient;
    private final String peerId = UUID.randomUUID().toString(); // Unique peer ID
    private String localIP;
    private int peerPort;

    // Error callback để UI có thể hiển thị error message
    private java.util.function.Consumer<String> downloadErrorCallback;
    private DashboardController dashboardController;

    @Override
    public void start(Stage primaryStage) throws IOException {
        // Ưu tiên lấy IP từ Config (client.bind.address) thay vì tự dò
        String bindIP = ConfigManager.get("client.bind.address", "");
        if (bindIP != null && !bindIP.isEmpty()) {
            localIP = bindIP; // Dùng IP do user chỉ định (Chính xác 100%)
        } else {
            localIP = NetworkUtil.getLocalIPAddress(); // Fallback
        }

        FlowLogger.logSection("CLIENT INITIALIZATION");
        FlowLogger.logInfo("Client Name: " + CLIENT_NAME, localIP);
        FlowLogger.logInfo("Listening IP (Correct): " + localIP, localIP); // Log rõ ràng đây là IP chính
        FlowLogger.logInfo("Tracker: " + TRACKER_URL, localIP);
        FlowLogger.logSeparator();

        // Tuần 6: Set up the UI first
        URL fxmlLocation = getClass().getResource("/fxml/Dashboard.fxml");
        if (fxmlLocation == null) {
            throw new IOException("Cannot find FXML file. Make sure it's in the resources/fxml directory.");
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        DashboardController controller = loader.getController();
        this.dashboardController = controller;
        controller.setMainApp(this);
        controller.setDownloadTasks(downloadTasks);

        setDownloadErrorCallback((errorMsg) -> {
            controller.showError(errorMsg);
        });

        Scene scene = new Scene(root, 900, 700);
        String cssPath = getClass().getResource("/styles/dashboard.css").toExternalForm();
        scene.getStylesheets().add(cssPath);

        primaryStage.setTitle("Multi-Source Downloader - " + CLIENT_NAME);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Tuần 6: Start a new download and add it to the queue
     * Can be called multiple times for multi-download support
     */
    public void startDownload(String manifestUrl) {
        FlowLogger.logSection("DOWNLOAD DEMO START"); // Đổi tên cho ngầu
        int step = 1;

        try {
            // 1. Fetch the manifest from the server
            // FlowLogger.logStep(step++, "Fetching manifest", localIP, 0,
            // extractIPFromUrl(ORIGIN_SERVER_URL), 8443); // Bỏ bớt log rườm rà
            ManifestModel manifest = fetchManifest(manifestUrl);

            // Fix: Replace localhost in manifest sources with actual Origin Server IP
            String originServerHost = extractIPFromUrl(ORIGIN_SERVER_URL);
            String originServerUrl = "http://" + originServerHost + ":8080";

            // Fix manifest silently (LOG GỌN: Chỉ báo tổng số)
            int fixedCount = 0;
            for (PieceModel piece : manifest.getPieces()) {
                List<String> fixedSources = new ArrayList<>();
                for (String source : piece.getSources()) {
                    if (source.contains("localhost:8080") || source.contains("127.0.0.1:8080")) {
                        String fixedSource = source.replace("http://localhost:8080", originServerUrl)
                                .replace("http://127.0.0.1:8080", originServerUrl);
                        fixedSources.add(fixedSource);
                        fixedCount++;
                    } else {
                        fixedSources.add(source);
                    }
                }
                piece.setSources(fixedSources);
            }
            if (fixedCount > 0) {
                System.out.println(
                        "[INFO] Auto-fixed " + fixedCount + " sources to point to Origin IP: " + originServerHost);
            }

            FlowLogger.logInfo("Manifest Loaded: " + manifest.getPieces().size() + " pieces", localIP);

            // 2. Define file identifiers and paths
            String fileId = manifestUrl.substring(manifestUrl.lastIndexOf('/') + 1);

            // USER REQUEST: Change download directory to D:\Downloads
            String downloadDir = "D:\\Downloads";
            try {
                java.nio.file.Path path = java.nio.file.Paths.get(downloadDir);
                if (!java.nio.file.Files.exists(path)) {
                    java.nio.file.Files.createDirectories(path);
                }
            } catch (Exception e) {
                // Fallback to user home if D:\ cannot be created/accessed
                System.err.println("Failed to use D:\\Downloads, falling back to User Home.");
                downloadDir = Paths.get(System.getProperty("user.home"), "Downloads").toString();
            }

            String localFilePath = Paths.get(downloadDir, fileId).toString();
            FlowLogger.logInfo("File ID: " + fileId + ", Local path: " + localFilePath, localIP);

            // 3. Initialize TrackerClient (only once)
            if (trackerClient == null) {
                FlowLogger.logStep(step++, "Initializing Tracker Client", localIP, 0, extractIPFromUrl(TRACKER_URL),
                        8081);
                trackerClient = new TrackerClient(httpClient, TRACKER_URL);
            }

            // 4. Set up storage components
            FlowLogger.logStep(step++, "Setting up storage", localIP, null);
            IStateStorage stateStorage = new JsonStateStorage();
            PieceStorage pieceStorage = new SparseFileStorage();

            // Reset state nếu lần trước download đã hoàn tất nhưng người dùng yêu cầu tải
            // lại
            handleExistingDownloadState(manifest, stateStorage, fileId, localFilePath);

            // Ensure the target file is created (as a sparse file)
            pieceStorage.createSparseFile(localFilePath, manifest.getFileSize());
            FlowLogger.logInfo("Sparse file created: " + localFilePath, localIP);

            // 5. Create and start the PeerServer (only once)
            if (peerServer == null) {
                try {
                    FlowLogger.logStep(step++, "Starting Peer Server", localIP, null);
                    peerServer = new PeerServer(stateStorage, pieceStorage, manifest, fileId, localFilePath);
                    peerServer.start();

                    peerPort = peerServer.getActualPort();

                    // Đợi server sẵn sàng trước khi announce (tối đa 5 giây)
                    boolean serverReady = peerServer.waitUntilReady(5000);
                    if (!serverReady) {
                        FlowLogger.logError("PeerServer may not be ready yet", localIP, "Timeout waiting for server");
                        System.err.println(
                                "⚠️ WARNING: PeerServer may not be ready yet. P2P sharing might not work properly.");
                    }

                    FlowLogger.logInfo("Peer Server started on " + localIP + ":" + peerPort, localIP);

                    // Announce với tracker sau khi PeerServer đã start và sẵn sàng
                    String[] peerParts = (localIP + ":" + peerPort).split(":");
                    FlowLogger.logStep(step++, "Announcing to Tracker", peerParts[0], Integer.parseInt(peerParts[1]),
                            extractIPFromUrl(TRACKER_URL), 8081);
                    boolean announced = trackerClient.announce(fileId, peerId, peerPort);
                    if (announced) {
                        FlowLogger.logInfo("Successfully announced to tracker: fileId=" + fileId + ", peer=" + localIP
                                + ":" + peerPort, localIP);
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
            FlowLogger.logStep(step++, "Fetching peer list from Tracker", localIP, 0, extractIPFromUrl(TRACKER_URL),
                    8081);
            enrichManifestWithPeers(manifest, fileId);

            // 7. Set up backend components
            FlowLogger.logStep(step++, "Setting up Download Client", localIP, null);
            // Allow more concurrent connections (10) than scheduler workers (4)
            // This ensures "On-Demand" streaming requests don't get blocked by background
            // tasks
            OkHttpDownloadClient okHttpClient = new OkHttpDownloadClient(httpClient, 10, manifest.getPieceSize(),
                    manifest.getFileSize());
            SourceTrackingDownloadClient downloadClient = new SourceTrackingDownloadClient(okHttpClient);

            ErrorCallback errorCallback = (piece, cause) -> {
                FlowLogger.logError("Download failed for piece " + piece.getId(), localIP, cause.getMessage());
                System.err.println("FATAL: Download failed for piece " + piece.getId());
                cause.printStackTrace();
            };

            // 8. Create the scheduler
            FlowLogger.logStep(step++, "Creating Scheduler", localIP, null);
            Scheduler scheduler = new Scheduler(manifest, downloadClient, errorCallback, 4, pieceStorage, stateStorage,
                    localFilePath, fileId);

            // 10. Create the download task
            DownloadTask task = new DownloadTask(fileId, scheduler);

            // 9. Mount the Virtual Filesystem (only once) - macOS/Linux only
            FlowLogger.logStep(step++, "Mounting Virtual Filesystem", localIP, null);
            mountVFS(fileId, manifest, scheduler, pieceStorage, stateStorage, localFilePath);

            // 9.5. Start Local Streaming Server (works on all OS: Windows, macOS, Linux)
            FlowLogger.logStep(step++, "Starting Local Streaming Server", localIP, null);
            startStreamingServer(task, fileId, manifest, scheduler, pieceStorage, stateStorage, localFilePath);

            // 11. Add the download task to the list
            downloadTasks.add(task);

            // 12. Setup source tracking callback
            downloadClient.setSourceTracker((sourceUrl) -> {
                // Track bytes downloaded from this source
                String sourceIP = extractIPFromUrl(sourceUrl);
                FlowLogger.logInfo("Piece downloaded from: " + sourceUrl, sourceIP);
                task.trackSourceBytes(sourceUrl, manifest.getPieceSize());
            });

            // 13. Start the download
            FlowLogger.logStep(step++, "Starting Download", localIP, null);
            FlowLogger.logSeparator();
            scheduler.start();

            // Implement "Metadata First" strategy to support immediate streaming of MP4
            // files
            applyMetadataFirstStrategy(scheduler, manifest);

            // 14. Periodically refresh peer list (background thread)
            startPeerRefreshThread(fileId, manifest);

        } catch (IOException e) {
            String errorMsg = "Download failed: " + e.getMessage();
            System.err.println(errorMsg);
            e.printStackTrace();

            // Notify UI about error
            if (downloadErrorCallback != null) {
                javafx.application.Platform.runLater(() -> {
                    downloadErrorCallback.accept(errorMsg);
                });
            }
        } catch (Exception e) {
            String errorMsg = "Unexpected error: " + e.getMessage();
            System.err.println(errorMsg);
            e.printStackTrace();

            // Notify UI about error
            if (downloadErrorCallback != null) {
                javafx.application.Platform.runLater(() -> {
                    downloadErrorCallback.accept(errorMsg);
                });
            }
        }
    }

    /**
     * Set error callback để UI có thể hiển thị error message
     */
    public void setDownloadErrorCallback(java.util.function.Consumer<String> callback) {
        this.downloadErrorCallback = callback;
    }

    /**
     * Fix manifest sources: Replace localhost với Origin Server IP thực tế
     */

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
            String selfAddress = localIP + ":" + peerPort; // Format chuẩn để so sánh

            for (String peerAddress : peers) {
                // Tránh thêm chính mình - so sánh exact match
                if (peerAddress.equals(selfAddress)) {
                    continue;
                }

                // Check for localhost/127.0.0.1 variation of self
                if ((peerAddress.startsWith("127.0.0.1") || peerAddress.startsWith("localhost"))
                        && peerAddress.endsWith(":" + peerPort)) {
                    continue;
                }

                // Encode fileId in URL to ensure it matches browser standard
                String encodedFileId = fileId.replace(" ", "%20");

                // Tạo peer URL cho piece này (Encoding fileId properly)
                String peerUrl = "http://" + peerAddress + "/piece/" + encodedFileId + "/" + piece.getId();

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
     * Background thread để refresh peer list định kỳ và re-enrich manifest
     */
    private void startPeerRefreshThread(String fileId, ManifestModel manifest) {
        Thread refreshThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(30000); // Refresh mỗi 30 giây

                    // Re-announce
                    if (peerServer != null && trackerClient != null) {
                        FlowLogger.logInfo("Refreshing peer list and re-announcing", localIP);
                        trackerClient.announce(fileId, peerId, peerServer.getActualPort());

                        // Re-enrich manifest với peers mới (quan trọng để nhận peers mới join)
                        enrichManifestWithPeers(manifest, fileId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Error in peer refresh thread: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        refreshThread.setDaemon(true);
        refreshThread.setName("peer-refresh-thread");
        refreshThread.start();
    }

    /**
     * Nếu state lưu trước đó đã hoàn tất (100%) hoặc không còn phù hợp với manifest
     * hiện tại,
     * tự động reset để đảm bảo lần tải mới thật sự thực hiện lại từ đầu.
     */
    private void handleExistingDownloadState(ManifestModel manifest, IStateStorage stateStorage,
            String fileId, String localFilePath) {
        try {
            DownloadState existingState = stateStorage.loadState(fileId);
            if (existingState == null) {
                return;
            }

            boolean pieceCountMismatch = existingState.getTotalPieces() != manifest.getPieces().size();
            boolean alreadyCompleted = existingState.getCompletedPieceCount() == existingState.getTotalPieces();

            if (pieceCountMismatch || alreadyCompleted) {
                String reason = pieceCountMismatch ? "piece count mismatch" : "already completed";
                FlowLogger.logInfo("Resetting stored state for " + fileId + " (" + reason + ")", localIP);

                DownloadState freshState = new DownloadState(manifest.getPieces().size());
                stateStorage.saveState(freshState, fileId);

                try {
                    java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(localFilePath));
                    FlowLogger.logInfo("Deleted previous local file before re-download: " + localFilePath, localIP);
                } catch (IOException ex) {
                    FlowLogger.logError("Failed to delete old file while resetting state", localIP, ex.getMessage());
                }
            }
        } catch (IOException e) {
            FlowLogger.logError("Failed to inspect/reset existing download state", localIP, e.getMessage());
        }
    }

    private void mountVFS(String fileId, ManifestModel manifest, Scheduler scheduler, PieceStorage pieceStorage,
            IStateStorage stateStorage, String localFilePath) {
        if (vfs == null) {
            new Thread(() -> {
                try {
                    VirtualDownloaderFS virtualDownloaderFS = new VirtualDownloaderFS(
                            manifest, scheduler, pieceStorage, stateStorage, localFilePath, fileId);

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
                    System.err.println(
                            "FATAL: Failed to mount virtual filesystem. The application will continue without it.");
                    e.printStackTrace();
                }
            }).start();
        }
    }

    /**
     * Start Local HTTP Streaming Server (works on all OS: Windows, macOS, Linux)
     * Cho phép truy cập file đang tải qua HTTP URL
     */
    private void startStreamingServer(DownloadTask task, String fileId, ManifestModel manifest, Scheduler scheduler,
            PieceStorage pieceStorage, IStateStorage stateStorage, String localFilePath) {
        new Thread(() -> {
            try {
                LocalStreamingServer server = new LocalStreamingServer(
                        manifest, scheduler, pieceStorage, stateStorage, localFilePath, fileId);

                // Store server in task for cleanup
                task.setStreamingServer(server);

                server.start();

                // Đợi server sẵn sàng (tối đa 5 giây)
                boolean serverReady = server.waitUntilReady(5000);
                if (!serverReady) {
                    System.err.println("⚠️ WARNING: Streaming server may not be ready yet. Playback might fail.");
                }

                // Update task with streaming URL
                final String streamUrl = server.getStreamingUrl();
                javafx.application.Platform.runLater(() -> {
                    task.setStreamingUrl(streamUrl);
                });

                System.out.println("✅ Local Streaming Server started!");
                System.out.println("📺 Streaming URL: " + streamUrl);
                System.out.println("💡 You can open this URL in:");
                System.out.println("   - Video players (VLC, MPV, etc.)");
                System.out.println("   - Web browsers (for download)");
                System.out.println("   - Any HTTP client");
                System.out.println("   - File will be downloaded on-demand when accessed");

                // Notify UI to play video directly (chỉ với MP4)
                if (dashboardController != null && fileId.toLowerCase().endsWith(".mp4")) {
                    // Thêm một chút delay để đảm bảo server hoàn toàn sẵn sàng
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    javafx.application.Platform.runLater(() -> {
                        dashboardController.playVideoStream(streamUrl, fileId);
                        dashboardController.updateVideoProgress(task);
                    });
                }
            } catch (Exception e) {
                System.err.println(
                        "WARNING: Failed to start Local Streaming Server. The application will continue without it.");
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private ManifestModel fetchManifest(String manifestUrl) throws IOException {
        String originIP = extractIPFromUrl(ORIGIN_SERVER_URL);
        System.out.println(String.format("[%s] [ORIGIN] GET %s | FROM: %s → TO: %s:8443",
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                manifestUrl, localIP, originIP));

        Request request = new Request.Builder().url(manifestUrl).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorMsg;
                if (response.code() == 404) {
                    errorMsg = "Manifest not found (404). File may not exist on server.";
                } else {
                    errorMsg = "Failed to download manifest: HTTP " + response.code();
                }
                System.err.println(String.format("[%s] [ORIGIN] ✗ Manifest request failed | Status: %d | FROM: %s:8443",
                        java.time.LocalDateTime.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                        response.code(), originIP));
                throw new IOException(errorMsg);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Manifest response body is null");
            }
            try (Reader reader = new InputStreamReader(body.byteStream())) {
                ManifestModel manifest = new Gson().fromJson(reader, ManifestModel.class);
                System.out.println(
                        String.format("[%s] [ORIGIN] ✓ Manifest received | FROM: %s:8443 → TO: %s | Pieces: %d",
                                java.time.LocalDateTime.now()
                                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                                originIP, localIP, manifest.getPieces().size()));
                return manifest;
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
        System.out.println("Stopping streaming servers...");

        // Close all streaming servers
        for (DownloadTask task : downloadTasks) {
            Object serverObj = task.getStreamingServer();
            if (serverObj instanceof LocalStreamingServer) {
                ((LocalStreamingServer) serverObj).close();
            }
        }

        System.out.println("Shutting down application...");
        if (peerServer != null) {
            peerServer.close();
        }

        // Tuần 6: Pause all downloads before shutdown
        downloadTasks.forEach(task -> task.getScheduler().pause());

        System.out.println("Shutdown complete.");
    }

    /**
     * Getter cho Origin Server URL - DashboardController cần để build manifest URL
     */
    public String getOriginServerUrl() {
        return ORIGIN_SERVER_URL;
    }

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Applies a "Metadata First" strategy for MP4 streaming.
     * Prioritizes downloading the beginning (Header) and end (MOOV atom) of the
     * file immediately.
     * This allows players to parse metadata and begin playback without waiting for
     * sequential download.
     */
    private void applyMetadataFirstStrategy(Scheduler scheduler, ManifestModel manifest) {
        new Thread(() -> {
            try {
                // Small delay to ensure scheduler worker pool is fully initialized
                Thread.sleep(200);
            } catch (InterruptedException refresh) {
                Thread.currentThread().interrupt();
            }

            System.out.println("🚀 [STRATEGY] Executing Metadata-First Download Strategy...");

            // 1. Header (Piece 0 & 1) logic is crucial connection establishment
            scheduler.downloadOnDemand(0);
            scheduler.downloadOnDemand(1);

            // 2. Footer (MOOV Atom) usually resides in the last few pieces of MP4 files
            int totalPieces = manifest.getPieces().size();
            if (totalPieces > 2) {
                // Download last 2 pieces to be safe
                scheduler.downloadOnDemand(totalPieces - 1);
                scheduler.downloadOnDemand(totalPieces - 2);
            }
        }).start();
    }
}
