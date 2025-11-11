import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.core.storage.PieceStorage;
import com.fragmented.download.javafx.logic.p2p.PeerServer;
import com.fragmented.download.javafx.logic.storage.JsonStateStorage;
import com.fragmented.download.javafx.logic.storage.SparseFileStorage;
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
import ru.serce.jnrfuse.AbstractFuseFS;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import ru.serce.jnrfuse.DokanFuse;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainApp extends Application {

    private static final String MANIFEST_URL = "http://localhost:8080/manifest/100MB.zip";
    private static final String VFS_MOUNT_DIR = "downloader-vfs";

    private Scheduler scheduler;
    private OkHttpDownloadClient downloadClient;
    private PeerServer peerServer;
    private AbstractFuseFS vfs; // Reference to the mounted filesystem for unmounting
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
        ErrorCallback errorCallback = (piece, cause) -> {
            System.err.println("FATAL: Download failed for piece " + piece.getId());
            cause.printStackTrace();
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
        VirtualDownloaderFS virtualDownloaderFS = new VirtualDownloaderFS(manifest, scheduler, pieceStorage, stateStorage, localFilePath);
        new Thread(() -> {
            try {
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
        // 8. Set up the UI
        URL fxmlLocation = getClass().getResource("/fxml/Dashboard.fxml");
        if (fxmlLocation == null) {
            throw new IOException("Cannot find FXML file. Make sure it's in the resources/fxml directory.");
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();
        // 9. Pass the scheduler to the controller
        DashboardController controller = loader.getController();
        controller.setScheduler(scheduler);

        primaryStage.setTitle("Multi-Source Downloader");
        primaryStage.setScene(new Scene(root, 800, 600));
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
        System.out.println("Unmounting virtual filesystem...");
        try {
            if (vfs != null) {
                vfs.unmount();
            }
        } catch (Throwable e) { // Catch Throwable to handle native errors as well
            System.err.println("Error while unmounting VFS: " + e.getMessage());
        }
        System.out.println("Shutting down application...");
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
