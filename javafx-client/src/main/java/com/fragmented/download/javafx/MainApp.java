package com.fragmented.download.javafx;

import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.core.storage.PieceStorage;
import com.fragmented.download.javafx.logic.p2p.PeerServer;
import com.fragmented.download.javafx.logic.storage.JsonStateStorage;
import com.fragmented.download.javafx.logic.storage.SparseFileStorage;
import com.fragmented.download.javafx.logic.vfs.VirtualDownloaderFS;
import com.fragmented.download.networking.OkHttpDownloadClient;
import com.fragmented.download.core.client.ErrorCallback;
import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.javafx.controller.DashboardController;
import com.fragmented.download.javafx.logic.Scheduler;
import com.google.gson.Gson;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainApp extends Application {

    private static final String MANIFEST_URL = "http://localhost:8080/manifest/100MB.zip";
    private static final String VFS_MOUNT_DIR = "downloader-vfs";

    private Scheduler scheduler;
    private OkHttpDownloadClient downloadClient;
    private PeerServer peerServer;
    private DashboardController dashboardController; // Tuần 3: Reference để trigger shake animation
    private VirtualDownloaderFS vfs; // Tuần 4: Reference to the mounted filesystem for unmounting
    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    public void start(Stage primaryStage) throws IOException {
        // 1. Fetch the manifest from the server
        ManifestModel manifest = fetchManifest();

        // 2. Define file identifiers and paths
        // In a real app, this would come from user input or another source
        String fileId = MANIFEST_URL.substring(MANIFEST_URL.lastIndexOf('/') + 1);
        String localFilePath = Paths.get(System.getProperty("user.home"), "Downloads", fileId).toString();

        // 3. Set up storage components
        IStateStorage stateStorage = new JsonStateStorage();
        PieceStorage pieceStorage = new SparseFileStorage();

        // Ensure the target file is created (as a sparse file)
        pieceStorage.createSparseFile(localFilePath, manifest.getFileSize());

        // 4. Set up backend components
        downloadClient = new OkHttpDownloadClient(httpClient, 4, manifest.getPieceSize());
        
        // Enhanced ErrorCallback - Tuần 2 & 3: Handle timeout, network errors, và SHA-256 mismatch
        ErrorCallback errorCallback = (piece, cause) -> {
            // Phân loại lỗi
            if (cause instanceof java.util.concurrent.TimeoutException) {
                System.err.println("TIMEOUT: Piece " + piece.getId() + " from source. Switching to alternative source...");
            } else if (cause instanceof java.io.IOException) {
                String message = cause.getMessage();
                if (message != null && message.contains("Hash mismatch")) {
                    // Tuần 3: SHA-256 verification failed
                    System.err.println("SHA-256 MISMATCH: Piece " + piece.getId() + " - " + message);
                } else {
                    System.err.println("NETWORK ERROR: Piece " + piece.getId() + " - " + message);
                }
            } else {
                System.err.println("FATAL: Download failed for piece " + piece.getId());
                cause.printStackTrace();
            }
            
            // Tuần 3: Trigger shake animation khi có lỗi
            if (dashboardController != null) {
                dashboardController.triggerShakeAnimation();
            }
            
            // Note: Scheduler sẽ tự động retry piece này qua source khác
            // do OkHttpDownloadClient đã có multi-source fallback logic
        };

        // 5. Create the scheduler, now with storage logic
        scheduler = new Scheduler(manifest, downloadClient, errorCallback, 4, pieceStorage, stateStorage, localFilePath, fileId);

        // 6. Create and start the PeerServer to serve downloaded pieces to others
        try {
            peerServer = new PeerServer(stateStorage, pieceStorage, manifest, fileId, localFilePath);
            peerServer.start();
        } catch (IOException e) {
            System.err.println("Failed to start PeerServer. P2P sharing will be disabled.");
            e.printStackTrace();
            // Optionally, show an alert to the user
        }
        
        // 7. Mount the Virtual Filesystem in a background thread to avoid UI freeze
        // Tuần 4: VirtualFS implementation with on-demand download
        VirtualDownloaderFS virtualDownloaderFS = new VirtualDownloaderFS(
            manifest, scheduler, pieceStorage, stateStorage, localFilePath, fileId
        );
        
        new Thread(() -> {
            try {
                Path mountPoint = Paths.get(System.getProperty("user.home"), VFS_MOUNT_DIR);
                if (!Files.exists(mountPoint)) {
                    Files.createDirectories(mountPoint);
                }
                System.out.println("Attempting to mount VFS at: " + mountPoint);

                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    System.err.println("WARNING: VirtualFS not supported on Windows (jnr-dokan unavailable).");
                    System.err.println("Application will continue without VirtualFS.");
                } else {
                    // macOS/Linux using FUSE
                    System.out.println("Running on macOS/Linux, using FUSE to mount.");
                    this.vfs = virtualDownloaderFS;
                    this.vfs.mount(mountPoint, true, false); // Blocking call
                }
            } catch (Exception e) {
                System.err.println("ERROR: Failed to mount virtual filesystem. The application will continue without it.");
                e.printStackTrace();
            }
        }, "vfs-mount-thread").start();
        
        // 8. Set up the UI
        URL fxmlLocation = getClass().getResource("/fxml/Dashboard.fxml");
        if (fxmlLocation == null) {
            throw new IOException("Cannot find FXML file. Make sure it's in the resources/fxml directory.");
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();
        // 9. Pass the scheduler to the controller
        dashboardController = loader.getController(); // Tuần 3: Store reference
        dashboardController.setScheduler(scheduler);

        // Tuần 2: Apply CSS Gradient styling
        Scene scene = new Scene(root, 800, 600);
        URL cssLocation = getClass().getResource("/styles/dashboard.css");
        if (cssLocation != null) {
            scene.getStylesheets().add(cssLocation.toExternalForm());
        } else {
            System.err.println("WARNING: CSS file not found. UI will use default styling.");
        }

        primaryStage.setTitle("Multi-Source Downloader");
        primaryStage.setScene(scene);
        primaryStage.show();

        // 10. Start the download
        scheduler.start();
    }

    private ManifestModel fetchManifest() throws IOException {
        Request request = new Request.Builder().url(MANIFEST_URL).build();
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
        // Clean up resources in reverse order of creation
        System.out.println("Shutting down application...");
        
        // Tuần 4: Unmount VirtualFS before closing other resources
        System.out.println("Unmounting virtual filesystem...");
        try {
            if (vfs != null) {
                vfs.umount();
            }
        } catch (Throwable e) { // Catch Throwable to handle native errors as well
            System.err.println("Error while unmounting VFS: " + e.getMessage());
        }
        
        if (peerServer != null) {
            peerServer.close();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (downloadClient != null) {
            downloadClient.shutdown();
        }
        System.out.println("Shutdown complete.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
