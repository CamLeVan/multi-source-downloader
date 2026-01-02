package com.fragmented.download.admin.service;

import com.fragmented.download.admin.model.FileSwarm;
import com.fragmented.download.admin.util.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service to interact with P2P Tracker Admin API
 */
public class TrackerService {
    
    private static final String BASE_URL = ConfigManager.get("tracker.url", "http://localhost:8081");
    private static final String API_BASE = BASE_URL + "/api/admin";
    
    private final OkHttpClient httpClient;
    private final Gson gson;
    
    public TrackerService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
    }
    
    /**
     * Get tracker statistics
     */
    public Map<String, Object> getStats() throws IOException {
        Request request = new Request.Builder()
                .url(API_BASE + "/stats")
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get stats: " + response.code());
            }
            
            String json = response.body().string();
            JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
            
            Map<String, Object> stats = new HashMap<>();
            if (jsonObject.has("totalFiles")) {
                stats.put("totalFiles", jsonObject.get("totalFiles").getAsInt());
            }
            if (jsonObject.has("totalPeers")) {
                stats.put("totalPeers", jsonObject.get("totalPeers").getAsInt());
            }
            if (jsonObject.has("files")) {
                stats.put("files", jsonObject.get("files"));
            }
            if (jsonObject.has("activePeersList")) {
                stats.put("activePeersList", jsonObject.get("activePeersList"));
            }
            
            return stats;
        }
    }
    
    /**
     * Get file swarms
     */
    public List<FileSwarm> getFileSwarms() throws IOException {
        Map<String, Object> stats = getStats();
        
        List<FileSwarm> swarms = new ArrayList<>();
        
        if (stats.containsKey("files")) {
            JsonArray filesArray = (JsonArray) stats.get("files");
            
            for (int i = 0; i < filesArray.size(); i++) {
                JsonObject fileObj = filesArray.get(i).getAsJsonObject();
                
                String fileId = fileObj.get("name").getAsString();
                int seeders = fileObj.get("seeders").getAsInt();
                
                FileSwarm swarm = new FileSwarm(fileId, seeders);
                
                if (fileObj.has("peers")) {
                    JsonArray peersArray = fileObj.get("peers").getAsJsonArray();
                    List<String> peers = new ArrayList<>();
                    for (int j = 0; j < peersArray.size(); j++) {
                        peers.add(peersArray.get(j).getAsString());
                    }
                    swarm.setPeers(peers);
                }
                
                swarms.add(swarm);
            }
        }
        
        return swarms;
    }
    
    /**
     * Remove a peer from a file swarm
     */
    public boolean removePeer(String fileId, String peerAddress) throws IOException {
        Request request = new Request.Builder()
                .url(API_BASE + "/files/" + fileId + "/peers/" + peerAddress)
                .delete()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to remove peer: " + response.code());
            }
            
            String json = response.body().string();
            JsonObject result = gson.fromJson(json, JsonObject.class);
            return result.has("success") && result.get("success").getAsBoolean();
        }
    }
    
    /**
     * Clear all peers for a file
     */
    public boolean clearFile(String fileId) throws IOException {
        Request request = new Request.Builder()
                .url(API_BASE + "/files/" + fileId)
                .delete()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to clear file: " + response.code());
            }
            
            String json = response.body().string();
            JsonObject result = gson.fromJson(json, JsonObject.class);
            return result.has("success") && result.get("success").getAsBoolean();
        }
    }
}

