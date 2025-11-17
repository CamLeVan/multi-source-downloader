package com.fragmented.download.javafx.logic.p2p;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Client để giao tiếp với P2P Tracker
 * Hỗ trợ announce và lấy danh sách peers
 */
public class TrackerClient {
    
    private static final Logger log = LoggerFactory.getLogger(TrackerClient.class);
    private static final String DEFAULT_TRACKER_URL = "http://localhost:8081";
    
    private final OkHttpClient httpClient;
    private final String trackerBaseUrl;
    private final Gson gson;
    
    public TrackerClient(OkHttpClient httpClient, String trackerBaseUrl) {
        this.httpClient = httpClient;
        this.trackerBaseUrl = trackerBaseUrl != null ? trackerBaseUrl : DEFAULT_TRACKER_URL;
        this.gson = new Gson();
    }
    
    public TrackerClient(OkHttpClient httpClient) {
        this(httpClient, DEFAULT_TRACKER_URL);
    }
    
    /**
     * Announce với tracker rằng peer này đang tải file
     * @param fileId ID của file
     * @param peerId ID của peer (có thể là UUID hoặc tên)
     * @param port Port mà PeerServer đang lắng nghe
     * @return true nếu thành công
     */
    public boolean announce(String fileId, String peerId, int port) {
        try {
            // Tạo JSON request manually (không dùng DTO để tránh dependency)
            String json = String.format("{\"fileId\":\"%s\",\"peerId\":\"%s\",\"port\":%d}", 
                    fileId, peerId, port);
            RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
            
            String announceUrl = trackerBaseUrl + "/tracker/announce";
            System.out.println(String.format("[%s] [TRACKER] POST %s | Payload: fileId=%s, peerId=%s, port=%d", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                announceUrl, fileId, peerId, port));
            
            Request httpRequest = new Request.Builder()
                    .url(announceUrl)
                    .post(body)
                    .build();
            
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String trackerIP = extractIPFromUrl(trackerBaseUrl);
                if (response.isSuccessful()) {
                    System.out.println(String.format("[%s] [TRACKER] ✓ Announce successful | FROM: %s → TO: %s:8081", 
                        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                        "Client", trackerIP));
                    log.info("Successfully announced to tracker for fileId: {}, port: {}", fileId, port);
                    return true;
                } else {
                    System.err.println(String.format("[%s] [TRACKER] ✗ Announce failed | Status: %d | TO: %s:8081", 
                        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                        response.code(), trackerIP));
                    log.warn("Failed to announce to tracker. Status: {}", response.code());
                    return false;
                }
            }
        } catch (IOException e) {
            String trackerIP = extractIPFromUrl(trackerBaseUrl);
            System.err.println(String.format("[%s] [TRACKER] ✗ Announce error | TO: %s:8081 | Error: %s", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                trackerIP, e.getMessage()));
            log.error("Error announcing to tracker", e);
            return false;
        }
    }
    
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
     * Lấy danh sách peers từ tracker
     * @param fileId ID của file
     * @return Set các peer addresses (dạng "ip:port")
     */
    public Set<String> getPeers(String fileId) {
        try {
            String url = trackerBaseUrl + "/tracker/peers?fileId=" + fileId;
            String trackerIP = extractIPFromUrl(trackerBaseUrl);
            
            System.out.println(String.format("[%s] [TRACKER] GET %s | FROM: Client → TO: %s:8081", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                url, trackerIP));
            
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                ResponseBody body = response.body();
                if (response.isSuccessful() && body != null) {
                    String json = body.string();
                    // Parse JSON array of strings
                    String[] peers = gson.fromJson(json, String[].class);
                    Set<String> peerSet = new HashSet<>();
                    if (peers != null && peers.length > 0) {
                        java.util.Collections.addAll(peerSet, peers);
                    }
                    
                    System.out.println(String.format("[%s] [TRACKER] ✓ Retrieved %d peers | FROM: %s:8081 → TO: Client", 
                        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                        peerSet.size(), trackerIP));
                    
                    log.info("Retrieved {} peers for fileId: {}", peerSet.size(), fileId);
                    return peerSet;
                } else {
                    System.err.println(String.format("[%s] [TRACKER] ✗ Get peers failed | Status: %d | FROM: %s:8081", 
                        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                        response != null ? response.code() : 0, trackerIP));
                    log.warn("Failed to get peers from tracker. Status: {}", response != null ? response.code() : "null");
                    return new HashSet<>();
                }
            }
        } catch (IOException e) {
            String trackerIP = extractIPFromUrl(trackerBaseUrl);
            System.err.println(String.format("[%s] [TRACKER] ✗ Get peers error | FROM: %s:8081 | Error: %s", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                trackerIP, e.getMessage()));
            log.error("Error getting peers from tracker", e);
            return new HashSet<>();
        }
    }
}

