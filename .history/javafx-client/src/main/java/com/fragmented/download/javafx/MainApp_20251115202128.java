package com.fragmented.download.javafx;

import com.fragmented.download.core.client.ErrorCallback;
import com.fragmented.download.core.model.DownloadState; // *** ĐÃ THÊM ***
import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.core.storage.PieceStorage;
import com.fragmented.download.javafx.controller.DashboardController;
import com.fragmented.download.javafx.logic.Scheduler;
import com.fragmented.download.javafx.logic.p2p.PeerServer;
import com.fragmented.download.javafx.logic.storage.JsonStateStorage;
import com.fragmented.download.javafx.logic.storage.SparseFileStorage;
import com.fragmented.download.javafx.logic.vfs.VirtualDownloaderFS; // *** ĐÃ THÊM (Giả sử bạn đã di chuyển file) ***
import com.fragmented.download.javafx.model.DownloadTask;
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
import ru.serce.jnrfuse.AbstractFuseFS;
import ru.serce.jnrfuse.DokanFuse;

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

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObservableList<DownloadTask> downloadTasks = FXCollections.observableArrayList();
    private PeerServer peerServer;
    private AbstractFuseFS vfs;

    @Override
    public void start(Stage primaryStage) throws IOException {
        // Set up the UI
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

    public void startDownload(String manifestUrl) {
        try {
            // 1. Fetch the manifest from the server
            ManifestModel manifest = fetchManifest(manifestUrl);

            // 2. Define file identifiers and paths
            String fileId = manifestUrl.substring(manifestUrl.lastIndexOf('/') + 1);
            String localFilePath = Paths.get(System.getProperty("user.home"), "Downloads", fileId).toString();

            // 3. Set up storage components
            IStateStorage stateStorage = new JsonStateStorage();
            PieceStorage pieceStorage = new SparseFileStorage();

            // Ensure the target file is created (as a sparse file)
            pieceStorage.createSparseFile(localFilePath, manifest.getFileSize());

            // 4. Set up backend components
            OkHttpDownloadClient downloadClient = new OkHttpDownloadClient(httpClient, 4, manifest.getPieceSize());
            ErrorCallback errorCallback = (piece, cause) -> {
                System.err.println("FATAL: Download failed for piece " + piece.getId());
                cause.printStackTrace();
            };

            // 5. Create the scheduler
            Scheduler scheduler = new Scheduler(manifest, downloadClient, errorCallback, 4, pieceStorage, stateStorage, localFilePath, fileId);
            
            // *** LẤY TRẠNG THÁI (IN-MEMORY) TỪ SCHEDULER ***
            // (Scheduler đã load hoặc tạo mới DownloadState)
            DownloadState inMemoryState = scheduler.getDownloadState(); // (Giả sử bạn thêm hàm getter này vào Scheduler)

            // 6. Create and start the PeerServer
            if (peerServer == null) {
                try {
                    // *** ĐÃ SỬA LỖI (P2P Bug) ***
                    // Truyền DownloadState (từ RAM), không phải IStateStorage (từ Đĩa)
                    peerServer = new PeerServer(inMemoryState, pieceStorage, manifest, fileId, localFilePath);
                    peerServer.start();
                } catch (IOException e) {
                    System.err.println("Failed to start PeerServer. P2P sharing will be disabled.");
                    e.printStackTrace();
                }
            }

            // 7. Mount the Virtual Filesystem
            // (Giả sử bạn đã di chuyển file VirtualDownloaderFS.java vào src/main/java...)
            mountVFS(fileId, manifest, scheduler, pieceStorage, inMemoryState, localFilePath);

            // 8. Create and add the download task to the list
            DownloadTask task = new DownloadTask(fileId, scheduler);
            downloadTasks.add(task);

            // 9. Start the download
            scheduler.start();

        } catch (IOException e) {
            e.printStackTrace();
            // In a real app, show an alert to the user
        }
    }

    /**
     * *** ĐÃ SỬA LỖI (VFS Bug) ***
     * Cập nhật hàm này để nhận DownloadState (từ RAM)
     */
    private void mountVFS(String fileId, ManifestModel manifest, Scheduler scheduler, PieceStorage pieceStorage, DownloadState inMemoryState, String localFilePath) {
        if (vfs == null) {
            new Thread(() -> {
                try {
                    // *** ĐÃ SỬA LỖI (VFS Bug) ***
                    // Truyền DownloadState (từ RAM), không phải IStateStorage (từ Đĩa)
                    VirtualDownloaderFS virtualDownloaderFS = new VirtualDownloaderFS(fileId, manifest, scheduler, pieceStorage, inMemoryState, localFilePath);
                    
                    Path mountPoint = Paths.get(System.getProperty("user.home"), VFS_MOUNT_DIR);
                    if (!Files.exists(mountPoint)) {
                        Files.createDirectories(mountPoint);
                    }
                    System.out.println("Attempting to mount VFS at: " + mountPoint);

                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        System.out.println("Running on Windows, using Dokan to mount.");
                        this.vfs = new DokanFuse(virtualDownloaderFS);
                        this.vfs.mount(mountPoint, true); // Blocking call
                    } else {
                        System.out.println("Running on macOS/Linux, using FUSE to mount.");
                        this.vfs = virtualDownloaderFS; // Assign instance for unmounting
                        this.vfs.mount(mountPoint, true); // Blocking call
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
                vfs.unmount();
            }
        } catch (Throwable e) {
            System.err.println("Error while unmounting VFS: " + e.getMessage());
        }
        System.out.println("Shutting down application...");
        if (peerServer != null) {
            peerServer.close();
        }

        // *** ĐÃ SỬA LỖI (Shutdown Bug) ***
        // Gọi .pause() (tắt an toàn) thay vì .shutdown() (tắt đột ngột)
        downloadTasks.forEach(task -> task.getScheduler().pause());
        
        System.out.println("Shutdown complete.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}