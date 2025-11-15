package com.fragmented.download.javafx.logic.vfs;

import com.fragmented.download.core.model.DownloadState;
import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.core.storage.PieceStorage;
import com.fragmented.download.javafx.logic.Scheduler;
import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Tuần 4: Virtual Filesystem implementation using jnr-fuse
 * Provides on-demand download when user accesses the file
 */
public class VirtualDownloaderFS extends FuseStubFS {

    private final ManifestModel manifest;
    private final Scheduler scheduler;
    private final PieceStorage pieceStorage;
    private final IStateStorage stateStorage;
    private final String localFilePath;
    private final String fileId;
    private final String fileName; // Extracted file name for VFS

    public VirtualDownloaderFS(ManifestModel manifest, Scheduler scheduler, 
                               PieceStorage pieceStorage, IStateStorage stateStorage, 
                               String localFilePath, String fileId) {
        this.manifest = manifest;
        this.scheduler = scheduler;
        this.pieceStorage = pieceStorage;
        this.stateStorage = stateStorage;
        this.localFilePath = localFilePath;
        this.fileId = fileId;
        // Extract just the file name from full path
        this.fileName = Paths.get(localFilePath).getFileName().toString();
    }

    /**
     * Tuần 4: getattr() - Provide file/directory metadata
     */
    @Override
    public int getattr(String path, FileStat stat) {
        // Root directory
        if ("/".equals(path)) {
            stat.st_mode.set(FileStat.S_IFDIR | 0755);
            stat.st_nlink.set(2);
            return 0;
        }

        // Our virtual file
        if (path.equals("/" + fileName)) {
            stat.st_mode.set(FileStat.S_IFREG | 0444); // Read-only file
            stat.st_nlink.set(1);
            stat.st_size.set(manifest.getFileSize());
            return 0;
        }

        return -ErrorCodes.ENOENT(); // File not found
    }

    /**
     * Tuần 4: readdir() - List files in directory
     */
    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filter, 
                       @off_t long offset, FuseFileInfo fi) {
        if (!"/".equals(path)) {
            return -ErrorCodes.ENOENT();
        }

        filter.apply(buf, ".", null, 0);
        filter.apply(buf, "..", null, 0);
        filter.apply(buf, fileName, null, 0);
        return 0;
    }

    /**
     * Tuần 4: read() - On-demand download trigger
     * Khi user đọc file (VLC, PDF viewer), tự động tải pieces cần thiết
     */
    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, 
                    FuseFileInfo fi) {
        if (!path.equals("/" + fileName)) {
            return -ErrorCodes.ENOENT();
        }

        // Don't read beyond file size
        if (offset >= manifest.getFileSize()) {
            return 0;
        }

        // Adjust size if reading beyond end of file
        long actualSize = Math.min(size, manifest.getFileSize() - offset);

        // Calculate which pieces are needed for this read
        long pieceSize = manifest.getPieceSize();
        int startPieceId = (int) (offset / pieceSize);
        int endPieceId = (int) ((offset + actualSize - 1) / pieceSize);

        try {
            // Load download state
            DownloadState state = stateStorage.loadState(fileId);
            if (state == null) {
                return -ErrorCodes.EIO(); // I/O error
            }

            // Check if all needed pieces are available, trigger download if not
            for (int pieceId = startPieceId; pieceId <= endPieceId; pieceId++) {
                if (!state.isPieceCompleted(pieceId)) {
                    // Tuần 4: Trigger on-demand download
                    scheduler.downloadOnDemand(pieceId);
                }
            }

            // Read data from sparse file storage
            byte[] data = pieceStorage.readPiece(localFilePath, offset, (int) actualSize);
            
            // Copy data to FUSE buffer
            buf.put(0, data, 0, data.length);
            return data.length;

        } catch (IOException e) {
            System.err.println("VirtualFS read error: " + e.getMessage());
            return -ErrorCodes.EIO();
        }
    }

    /**
     * open() - Called when file is opened
     */
    @Override
    public int open(String path, FuseFileInfo fi) {
        if (!path.equals("/" + fileName)) {
            return -ErrorCodes.ENOENT();
        }
        return 0; // Success
    }
}

