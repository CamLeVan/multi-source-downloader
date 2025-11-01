package com.fragmented.download.javafx.logic.storage;

import com.fragmented.download.core.model.DownloadState;
import com.fragmented.download.core.storage.IStateStorage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * An implementation of IStateStorage that saves the download state as a JSON file
 * in the user's home directory.
 */
public class JsonStateStorage implements IStateStorage {

    private final Path storageDir;
    private final Gson gson;

    public JsonStateStorage() {
        // Store state in a .downloader/state directory in the user's home folder.
        String userHome = System.getProperty("user.home");
        this.storageDir = Paths.get(userHome, ".downloader", "state");
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            // Using a runtime exception as this is a critical failure on startup.
            throw new RuntimeException("Could not create state storage directory: " + this.storageDir, e);
        }
        this.gson = new GsonBuilder().create();
    }

    /**
     * Generates a sanitized file path for a given fileId.
     * @param fileId The unique ID of the file.
     * @return A File object pointing to the JSON state file.
     */
    private File getFileForId(String fileId) {
        // Sanitize fileId to create a valid filename.
        String sanitizedId = fileId.replaceAll("[^a-zA-Z0-9.-]", "_");
        return storageDir.resolve(sanitizedId + ".state.json").toFile();
    }

    @Override
    public void saveState(DownloadState state, String fileId) throws IOException {
        File stateFile = getFileForId(fileId);
        try (FileWriter writer = new FileWriter(stateFile)) {
            gson.toJson(state, writer);
        }
    }

    @Override
    public DownloadState loadState(String fileId) throws IOException {
        File stateFile = getFileForId(fileId);
        if (!stateFile.exists() || stateFile.length() == 0) {
            return null; // No state saved yet.
        }
        try (FileReader reader = new FileReader(stateFile)) {
            DownloadState state = gson.fromJson(reader, DownloadState.class);
            // If the file is empty or corrupted, gson might return null.
            return state;
        }
    }
}
