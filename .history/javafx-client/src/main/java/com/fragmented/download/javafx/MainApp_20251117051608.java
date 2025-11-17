package com.fragmented.download.javafx;

import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.core.storage.PieceStorage;
import com.fragmented.download.javafx.logic.p2p.PeerServer;
import com.fragmented.download.javafx.logic.storage.JsonStateStorage;
import com.fragmented.download.javafx.logic.storage.SparseFileStorage;
import com.fragmented.download.javafx.logic.vfs.VirtualDownloaderFS;
import com.fragmented.download.javafx.model.DownloadTask;
import com.fragmented.download.networking.OkHttpDownloadClient;
import com.fragmented.download.core.client.ErrorCallback;
import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.javafx.controller.DashboardController;
import com.fragmented.download.javafx.logic.Scheduler;
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

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.Collection;

/**
 * Tuần 6: MainApp với multi-download support
 */
public class MainApp extends Application {

    private static final String VFS_MOUNT_DIR = "downloader-vfs";

    private OkHttpClient httpClient;
    private final ObservableList<DownloadTask> downloadTasks = FXCollections.observableArrayList(); // Tuần 6: Multi-download
    private PeerServer peerServer;
    private VirtualDownloaderFS vfs;

    @Override
    public void start(Stage primaryStage) throws IOException {
        // Tuần 6: Set up the UI first
        // Cấu hình HTTP client để tin tưởng chứng chỉ tự ký của server
        this.httpClient = createTrustingOkHttpClient();

        URL fxmlLocation = getClass().getResource("/fxml/Dashboard.fxml");
        if (fxmlLocation == null) {
            throw new IOException("Cannot find FXML file. Make sure it's in the resources/fxml directory.");
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();
        
        DashboardController controller = loader.getController();
        controller.setMainApp(this);
        controller.setDownloadTasks(downloadTasks);

        // Khởi tạo các dịch vụ nền (P2P, VFS) một lần duy nhất khi ứng dụng khởi động
        initializeBackgroundServices();

        primaryStage.setTitle("Multi-Source Downloader");
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();
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
            
            // 6. Cập nhật VFS và PeerServer với thông tin tệp mới (nếu chúng đã được khởi tạo)
            // Lưu ý: Cần điều chỉnh lại VFS và PeerServer để hỗ trợ nhiều tệp
            // Ví dụ: vfs.addFile(fileId, manifest, scheduler, ...);
            // peerServer.addFile(fileId, manifest, ...);

            // 7. Create and add the download task to the list
            DownloadTask task = new DownloadTask(fileId, scheduler);
            downloadTasks.add(task);

            // 9. Start the download
            scheduler.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initializeBackgroundServices() {
        if (vfs == null) {
            new Thread(() -> {
                try {
                    // TODO: Refactor VirtualDownloaderFS và PeerServer để không phụ thuộc vào một manifest duy nhất
                    // Khởi tạo chúng ở đây mà không cần thông tin tệp cụ thể
                    // this.peerServer = new PeerServer(...);
                    // this.peerServer.start();

                    // this.vfs = new VirtualDownloaderFS(...);
                    
                    Path mountPoint = Paths.get(System.getProperty("user.home"), VFS_MOUNT_DIR);
                    if (!Files.exists(mountPoint)) {
                        Files.createDirectories(mountPoint);
                    }
                    System.out.println("Attempting to mount VFS at: " + mountPoint);
    
                    String os = System.getProperty("os.name").toLowerCase();
                    // Kích hoạt VFS cho cả Windows (với Dokan) và macOS/Linux (với FUSE)
                    if (os.contains("win") || os.contains("mac") || os.contains("nix") || os.contains("nux")) {
                        System.out.println("Running on macOS/Linux, using FUSE to mount.");
                        // this.vfs.mount(mountPoint, true, false);
                    }
                } catch (Exception e) {
                    System.err.println("FATAL: Failed to initialize background services (VFS/PeerServer).");
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

    /**
     * Tạo một OkHttpClient được cấu hình để tin tưởng chứng chỉ tự ký của Origin Server.
     * Điều này rất quan trọng để kết nối HTTPS tới server đang chạy ở local.
     *
     * @return Một instance của OkHttpClient đã được cấu hình.
     */
    private OkHttpClient createTrustingOkHttpClient() {
        try {
            // Tải chứng chỉ từ tệp (giả sử bạn đã xuất cert từ keystore.p12)
            // Bạn cần đặt tệp "origin-server.crt" trong thư mục resources.
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            InputStream certInputStream = MainApp.class.getResourceAsStream("/origin-server.crt");
            if (certInputStream == null) {
                System.err.println("WARNING: Certificate file 'origin-server.crt' not found. Using insecure trust-all manager. This is not recommended.");
                // Fallback về giải pháp không an toàn nếu không tìm thấy file cert
                return getInsecureOkHttpClient();
            }
            Certificate ca;
            try (InputStream caInput = certInputStream) {
                ca = cf.generateCertificate(caInput);
            }

            // Tạo một KeyStore chứa chứng chỉ của chúng ta
            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);

            // Tạo một TrustManager sử dụng KeyStore của chúng ta
            String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            tmf.init(keyStore);

            // Tạo một SSLContext sử dụng TrustManager của chúng ta
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            // Cuối cùng, tạo OkHttpClient với SSLSocketFactory và TrustManager đã cấu hình
            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) tmf.getTrustManagers()[0])
                    .build();
        } catch (Exception e) {
            System.err.println("Error creating trusting OkHttpClient. Falling back to insecure client.");
            return getInsecureOkHttpClient(); // Fallback nếu có lỗi
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

    /**
     * Trả về một OkHttpClient KHÔNG AN TOÀN, tin tưởng tất cả các chứng chỉ.
     * CHỈ SỬ DỤNG CHO MỤC ĐÍCH PHÁT TRIỂN VÀ DEBUG.
     */
    private static OkHttpClient getInsecureOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return new OkHttpClient.Builder().sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]).hostnameVerifier((hostname, session) -> true).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
