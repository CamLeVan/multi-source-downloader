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

        primaryStage.setTitle("Multi-Source Downloader");
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
        try {
            // 1. Fetch the manifest from the server
            ManifestModel manifest = fetchManifest(manifestUrl);

            // 2. Define file identifiers and paths
            String fileId = manifestUrl.substring(manifestUrl.lastIndexOf('/') + 1);
            String localFilePath = Paths.get(System.getProperty("user.home"), "Downloads", fileId).toString();

            // 3. Initialize TrackerClient (only once)
            if (trackerClient == null) {
                trackerClient = new TrackerClient(httpClient, TRACKER_URL);
            }

            // 4. Set up storage components
            IStateStorage stateStorage = new JsonStateStorage();
            PieceStorage pieceStorage = new SparseFileStorage();

            // Ensure the target file is created (as a sparse file)
            pieceStorage.createSparseFile(localFilePath, manifest.getFileSize());

            // 5. Create and start the PeerServer (only once)
            if (peerServer == null) {
                try {
                    peerServer = new PeerServer(stateStorage, pieceStorage, manifest, fileId, localFilePath);
                    peerServer.start();
                    
                    // Announce với tracker sau khi PeerServer đã start
                    int peerPort = peerServer.getActualPort();
                    trackerClient.announce(fileId, peerId, peerPort);
                    System.out.println("Announced to tracker: fileId=" + fileId + ", port=" + peerPort);
                } catch (IOException e) {
                    System.err.println("Failed to start PeerServer. P2P sharing will be disabled.");
                    e.printStackTrace();
                }
            }

            // 6. Enrich manifest với peer sources từ tracker
            enrichManifestWithPeers(manifest, fileId);

            // 7. Set up backend components
            OkHttpDownloadClient okHttpClient = new OkHttpDownloadClient(httpClient, 4, manifest.getPieceSize());
            SourceTrackingDownloadClient downloadClient = new SourceTrackingDownloadClient(okHttpClient);
            
            ErrorCallback errorCallback = (piece, cause) -> {
                System.err.println("FATAL: Download failed for piece " + piece.getId());
                cause.printStackTrace();
            };

            // 8. Create the scheduler
            Scheduler scheduler = new Scheduler(manifest, downloadClient, errorCallback, 4, pieceStorage, stateStorage, localFilePath, fileId);

            // 9. Mount the Virtual Filesystem (only once)
            mountVFS(fileId, manifest, scheduler, pieceStorage, stateStorage, localFilePath);

            // 10. Create and add the download task to the list
            DownloadTask task = new DownloadTask(fileId, scheduler);
            downloadTasks.add(task);
            
            // 11. Setup source tracking callback
            downloadClient.setSourceTracker((sourceUrl) -> {
                // Track bytes downloaded from this source
                // Note: bytes will be tracked in Scheduler when piece completes
                task.trackSourceBytes(sourceUrl, manifest.getPieceSize());
            });

            // 12. Start the download
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
            System.out.println("No peers found for fileId: " + fileId);
            return;
        }

        System.out.println("Found " + peers.size() + " peers for fileId: " + fileId);
        
        // Thêm peer URLs vào sources của mỗi piece
        for (PieceModel piece : manifest.getPieces()) {
            List<String> sources = new ArrayList<>(piece.getSources());
            
            // Thêm peer URLs vào sources (format: http://ip:port/piece/fileId/pieceId)
            for (String peerAddress : peers) {
                // Tránh thêm chính mình
                if (peerServer != null) {
                    String myAddress = "localhost:" + peerServer.getActualPort();
                    if (peerAddress.contains(myAddress)) {
                        continue;
                    }
                }
                
                // Tạo peer URL cho piece này
                String peerUrl = "http://" + peerAddress + "/piece/" + fileId + "/" + piece.getId();
                if (!sources.contains(peerUrl)) {
                    sources.add(peerUrl);
                }
            }
            
            piece.setSources(sources);
        }
        
        System.out.println("Enriched manifest with peer sources");
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
