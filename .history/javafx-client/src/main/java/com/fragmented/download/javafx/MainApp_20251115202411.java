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
            OkHttpDownloadClient downloadClient = new Ok