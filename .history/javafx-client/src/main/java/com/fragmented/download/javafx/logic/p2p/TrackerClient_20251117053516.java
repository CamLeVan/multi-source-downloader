package com.fragmented.download.javafx.logic.p2p;

import com.fragmented.download.p2p.dto.AnnounceRequest;
import com.google.gson.Gson;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

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
            AnnounceRequest request = new AnnounceRequest();
            request.setFileId(fileId);
            request.setPeerId(peerId);
            request.setPort(port);
            
            String json = gson.toJson(request);
            RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
            
            Request httpRequest = new Request.Builder()
                    .url(trackerBaseUrl + "/tracker/announce")
                    .post(body)
                    .build();
            
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (response.isSuccessful()) {
                    log.info("Successfully announced to tracker for fileId: {}, port: {}", fileId, port);
                    return true;
                } else {
                    log.warn("Failed to announce to tracker. Status: {}", response.code());
                    return false;
                }
            }
        } catch (IOException e) {
            log.error("Error announcing to tracker", e);
            return false;
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
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    // Parse JSON array of strings
                    String[] peers = gson.fromJson(json, String[].class);
                    Set<String> peerSet = new HashSet<>();
                    for (String peer : peers) {
                        peerSet.add(peer);
                    }
                    log.info("Retrieved {} peers for fileId: {}", peerSet.size(), fileId);
                    return peerSet;
                } else {
                    log.warn("Failed to get peers from tracker. Status: {}", response != null ? response.code() : "null");
                    return new HashSet<>();
                }
            }
        } catch (IOException e) {
            log.error("Error getting peers from tracker", e);
            return new HashSet<>();
        }
    }
}

