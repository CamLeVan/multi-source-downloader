package com.fragmented.download.javafx.logic.vfs;

import com.fragmented.download.core.model.DownloadState;
import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.core.storage.PieceStorage;
import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.javafx.logic.Scheduler;
import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFileInfo;
import ru.serce.jnrfuse.FuseGetattrSetter;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;

import static jnr.constants.platform.linux.Errno.ENOENT;
import static jnr.constants.platform.linux.Errno.EIO;

public class VirtualDownloaderFS extends FuseStubFS {

    private final String fileName;
    private final ManifestModel manifest;
    private final Scheduler scheduler;
    private final PieceStorage pieceStorage;
    private final IStateStorage stateStorage;
    private final String localFilePath;

    public VirtualDownloaderFS(String fileName, ManifestModel manifest, Scheduler scheduler, PieceStorage pieceStorage, IStateStorage stateStorage, String localFilePath) {
        this.fileName = fileName;
        this.manifest = manifest;
        this.scheduler = scheduler;
        this.pieceStorage = pieceStorage;
        this.stateStorage = stateStorage;
        this.localFilePath = localFilePath;
    }

    @Override
    public int getattr(String path, FuseGetattrSetter getattrSetter) {
        if ("/".equals(path)) {
            getattrSetter.set(
                    0, 0, 0, 0, 0, 0, 0, 0,
                    FileStat.S_IFDIR | 0755, 1, 0, 0, 0
            );
            return 0;
        }

        if (path.equals("/" + this.fileName)) {
            getattrSetter.set(
                    0, 0, 0, 0, 0, 0, manifest.getFileSize(), 0,
                    FileStat.S_IFREG | 0444, 1, 0, 0, 0 // Read-only for users
            );
            return 0;
        }

        return -ENOENT();
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        if (!path.equals("/" + this.fileName)) {
            return -ENOENT();
        }

        // Prevent reading past the end of the file
        if (offset >= manifest.getFileSize()) {
            return 0;
        }
        if (offset + size > manifest.getFileSize()) {
            size = manifest.getFileSize() - offset;
        }

        long pieceSize = manifest.getPieceSize();
        int startIndex = (int) (offset / pieceSize);
        int endIndex = (int) ((offset + size - 1) / pieceSize);

        try {
            DownloadState currentState = stateStorage.loadState(scheduler.getFileId());
            if (currentState == null) {
                // This should not happen if scheduler is initialized correctly
                return -EIO();
            }

            boolean needsDownload = IntStream.rangeClosed(startIndex, endIndex)
                    .anyMatch(i -> !currentState.isPieceCompleted(i));

            if (needsDownload) {
                System.out.println("VFS: Pieces from " + startIndex + " to " + endIndex + " are missing. Triggering on-demand download.");
                CompletableFuture<Void> downloadFuture = scheduler.downloadPiecesOnDemand(startIndex, endIndex);
                // This is the crucial fix: block the FUSE thread until the download is complete.
                downloadFuture.get();
                System.out.println("VFS: On-demand download complete for pieces " + startIndex + "-" + endIndex);
            }

            // After waiting, the data is guaranteed to be on disk.
            byte[] data = pieceStorage.readPiece(localFilePath, offset, (int) size);
            if (data == null || data.length == 0) {
                return 0;
            }

            buf.put(0, data, 0, data.length);
            return data.length;

        } catch (IOException | InterruptedException | ExecutionException e) {
            System.err.println("VFS: Failed during on-demand read operation for pieces " + startIndex + "-" + endIndex);
            e.printStackTrace();
            return -EIO(); // Return an I/O error to the calling application
        }
    }
}