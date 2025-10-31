package com.fragmented.download.core.model;

import java.util.List;

public class PieceModel {
    private int id;
    private String sha256;
    private List<String> sources;

    public PieceModel(int id, String sha256, List<String> sources) {
        this.id = id;
        this.sha256 = sha256;
        this.sources = sources;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources;
    }
}
