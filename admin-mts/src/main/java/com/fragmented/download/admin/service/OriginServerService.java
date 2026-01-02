package com.fragmented.download.admin.service;

import com.fragmented.download.admin.model.FileInfo;
import com.fragmented.download.admin.model.ServerStats;
import com.fragmented.download.admin.util.ConfigManager;
import com.fragmented.download.core.model.FileInfoDTO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service to interact with Origin Server Admin API
 */
public class OriginServerService {
    
    private static final String BASE_URL = ConfigManager.get("origin.server.url", "http://localhost:8080");
    private static final String API_BASE = BASE_URL + "/api/admin";
    
    private final OkHttpClient httpClient;
    private final Gson gson;
    
    public OriginServerService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
                .build();
        this.gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                .create();
    }
    
    /**
     * Get server statistics
     */
    public ServerStats getStats() throws IOException {
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
            
            ServerStats stats = new ServerStats();
            if (jsonObject.has("totalFiles")) {
                stats.setTotalFiles(jsonObject.get("totalFiles").getAsInt());
            }
            if (jsonObject.has("totalSize")) {
                stats.setTotalSize(jsonObject.get("totalSize").getAsLong());
            }
            if (jsonObject.has("totalSizeFormatted")) {
                stats.setTotalSizeFormatted(jsonObject.get("totalSizeFormatted").getAsString());
            }
            if (jsonObject.has("diskFree")) {
                stats.setDiskFree(jsonObject.get("diskFree").getAsLong());
            }
            if (jsonObject.has("diskTotal")) {
                stats.setDiskTotal(jsonObject.get("diskTotal").getAsLong());
            }
            if (jsonObject.has("diskFreeFormatted")) {
                stats.setDiskFreeFormatted(jsonObject.get("diskFreeFormatted").getAsString());
            }
            if (jsonObject.has("diskTotalFormatted")) {
                stats.setDiskTotalFormatted(jsonObject.get("diskTotalFormatted").getAsString());
            }
            if (jsonObject.has("diskUsagePercent")) {
                stats.setDiskUsagePercent(jsonObject.get("diskUsagePercent").getAsDouble());
            }
            
            return stats;
        }
    }
    
    /**
     * Get list of files
     */
    public List<FileInfo> getFiles() throws IOException {
        Request request = new Request.Builder()
                .url(API_BASE + "/files")
                .get()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get files: " + response.code());
            }
            
            String json = response.body().string();
            // Deserialize to DTOs first
            List<FileInfoDTO> dtos = gson.fromJson(json, new TypeToken<List<FileInfoDTO>>(){}.getType());
            
            // Convert DTOs to JavaFX models
            List<FileInfo> files = new ArrayList<>();
            for (FileInfoDTO dto : dtos) {
                files.add(new FileInfo(dto));
            }
            
            return files;
        }
    }
    
    /**
     * Delete a file
     */
    public boolean deleteFile(String fileName) throws IOException {
        Request request = new Request.Builder()
                .url(API_BASE + "/files/" + fileName)
                .delete()
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to delete file: " + response.code());
            }
            
            String json = response.body().string();
            JsonObject result = gson.fromJson(json, JsonObject.class);
            return result.has("success") && result.get("success").getAsBoolean();
        }
    }
    
    /**
     * Upload a file
     */
    public Map<String, Object> uploadFile(File file, boolean generateManifest) throws IOException {
        RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/octet-stream"));
        
        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), fileBody)
                .addFormDataPart("generateManifest", String.valueOf(generateManifest));
        
        Request request = new Request.Builder()
                .url(API_BASE + "/files/upload")
                .post(builder.build())
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to upload file: " + response.code());
            }
            
            String json = response.body().string();
            return gson.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
        }
    }
    
    /**
     * Regenerate manifest for a file
     */
    public boolean regenerateManifest(String fileName) throws IOException {
        Request request = new Request.Builder()
                .url(API_BASE + "/files/" + fileName + "/regenerate-manifest")
                .post(RequestBody.create("", MediaType.parse("application/json")))
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to regenerate manifest: " + response.code());
            }
            
            String json = response.body().string();
            JsonObject result = gson.fromJson(json, JsonObject.class);
            return result.has("success") && result.get("success").getAsBoolean();
        }
    }
}

