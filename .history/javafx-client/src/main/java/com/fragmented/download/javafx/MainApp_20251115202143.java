package com.fragmented.download.javafx;

import com.fragmented.download.core.client.ErrorCallback;
import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.core.storage.PieceStorage;
import com.fragmented.download.javafx.controller.DashboardController;
import com.fragmented.download.javafx.logic.Scheduler;
import com.fragmented.download.javafx.logic.p2p.PeerServer;
import com.fragmented.download.javafx.logic.storage.JsonStateStorage;
import com.fragmented.download.javafx.logic.storage.SparseFileStorage;
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

            // 6. Create and start the PeerServer if it's not already running
            if (peerServer == null) {
                try {
                    peerServer = new PeerServer(stateStorage, pieceStorage, manifest, fileId, localFilePath);
                    peerServer.start();
                } catch (IOException e) {
                    System.err.println("Failed to start PeerServer. P2P sharing will be disabled.");
                    e.printStackTrace();
                }
            }

            // 7. Mount the Virtual Filesystem in a background thread to avoid UI freeze
            mountVFS(manifest, scheduler, pieceStorage, stateStorage, localFilePath);

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

    private void mountVFS(ManifestModel manifest, Scheduler scheduler, PieceStorage pieceStorage, IStateStorage stateStorage, String localFilePath) {
        if (vfs == null) {
            new Thread(() -> {
                try {
                    // *** ĐÃ SỬA LỖI ***
                    // Sửa lại lời gọi constructor cho đúng với định nghĩa của VirtualDownloaderFS.
                    // Cần truyền vào `fileName` và `downloadState` (lấy từ scheduler).
                    // Bỏ `stateStorage` vì VFS không cần nó nữa.
                    String fileName = manifest.getFileName();
                    VirtualDownloaderFS virtualDownloaderFS = new VirtualDownloaderFS(
                            fileName, manifest, scheduler, pieceStorage, scheduler.getDownloadState(), localFilePath
                    );
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
        downloadTasks.forEach(task -> task.getScheduler().shutdown());
        System.out.println("Shutdown complete.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
