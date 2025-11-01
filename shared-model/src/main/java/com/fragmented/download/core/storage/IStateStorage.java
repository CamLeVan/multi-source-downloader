package com.fragmented.download.core.storage;

import com.fragmented.download.core.model.DownloadState;

import java.io.IOException;

/**
 * Defines the contract for persisting and retrieving the download state of a file.
 */
public interface IStateStorage {

    /**
     * Saves the current download state for a given file.
     * @param state The DownloadState object to persist.
     * @param fileId A unique identifier for the file (e.g., its hash or a unique name).
     * @throws IOException If an I/O error occurs during saving.
     */
    void saveState(DownloadState state, String fileId) throws IOException;

    /**
     * Loads the download state for a given file.
     * @param fileId A unique identifier for the file.
     * @return The loaded DownloadState object, or null if no state is found.
     * @throws IOException If an I/O error occurs during loading.
     */
    DownloadState loadState(String fileId) throws IOException;
}
