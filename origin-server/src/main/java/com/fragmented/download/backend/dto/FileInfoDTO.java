package com.fragmented.download.backend.dto;

import java.time.LocalDateTime;

/**
 * DTO để trả về thông tin metadata của file
 * Dùng cho API /files/info
 */
public class FileInfoDTO {
    private String fileName;
    private long size; // bytes
    private LocalDateTime lastModified;
    private int mirrorsCount; // Số lượng mirrors có sẵn (hiện tại = 0, sẽ tính sau)
    private boolean hasManifest;

    public FileInfoDTO() {
    }

    public FileInfoDTO(String fileName, long size, LocalDateTime lastModified, boolean hasManifest) {
        this.fileName = fileName;
        this.size = size;
        this.lastModified = lastModified;
        this.mirrorsCount = 0; // Default, sẽ tính sau
        this.hasManifest = hasManifest;
    }

    // Getters and Setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public int getMirrorsCount() {
        return mirrorsCount;
    }

    public void setMirrorsCount(int mirrorsCount) {
        this.mirrorsCount = mirrorsCount;
    }

    public boolean isHasManifest() {
        return hasManifest;
    }

    public void setHasManifest(boolean hasManifest) {
        this.hasManifest = hasManifest;
    }
}

