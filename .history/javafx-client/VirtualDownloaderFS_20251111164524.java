package com.fragmented.download.javafx.logic.vfs;

import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.core.storage.IPieceStorage;
import com.fragmented.download.core.storage.IStateStorage;
import com.fragmented.download.javafx.logic.Scheduler;
import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import ru.serce.jnrfuse.FuseFileInfo;
import ru.serce.jnrfuse.FuseGetattrSetter;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;

import java.nio.file.Path;
import java.util.stream.IntStream;

import static jnr.constants.platform.windows.WinError.ENOENT;

public class VirtualDownloaderFS extends FuseStubFS {

    private final ManifestModel manifest;
    private final Scheduler scheduler;
    private final IPieceStorage pieceStorage;
    private final IStateStorage stateStorage;
    private final String localFilePath;

    public VirtualDownloaderFS(ManifestModel manifest, Scheduler scheduler, IPieceStorage pieceStorage, IStateStorage stateStorage, String localFilePath) {
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

        if (path.equals("/" + manifest.getFileName())) {
            getattrSetter.set(
                    0, 0, 0, 0, 0, 0, manifest.getTotalSize(), 0,
                    FileStat.S_IFREG | 0644, 1, 0, 0, 0
            );
            return 0;
        }

        return -ENOENT();
    }

    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        if (!path.equals("/" + manifest.getFileName())) {
            return -ENOENT();
        }

        long pieceSize = manifest.getPieceSize();
        int startIndex = (int) (offset / pieceSize);
        int endIndex = (int) ((offset + size - 1) / pieceSize);

        boolean allPiecesAvailable = IntStream.rangeClosed(startIndex, endIndex)
                .allMatch(stateStorage::hasPiece);

        if (!allPiecesAvailable) {
            scheduler.downloadPiecesOnDemand(startIndex, endIndex);
        }

        byte[] data = pieceStorage.read(offset, (int) size);
        if (data == null || data.length == 0) {
            return 0;
        }

        buf.put(0, data, 0, data.length);
        return data.length;
    }

    public void mount(Path mountPoint) {
        try {
            mount(mountPoint, true, false);
        } catch (Exception e) {
            System.err.println("Failed to mount VFS: " + e.getMessage());
            e.printStackTrace();
        }
    }
}