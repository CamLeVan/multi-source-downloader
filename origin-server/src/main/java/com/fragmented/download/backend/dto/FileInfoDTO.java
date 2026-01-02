package com.fragmented.download.backend.dto;

/**
 * @deprecated Use com.fragmented.download.core.model.FileInfoDTO instead
 * This class is kept for backward compatibility only
 * 
 * This is now just an alias - all code should migrate to use
 * com.fragmented.download.core.model.FileInfoDTO directly
 */
@Deprecated
public class FileInfoDTO extends com.fragmented.download.core.model.FileInfoDTO {
    public FileInfoDTO() {
        super();
    }

    public FileInfoDTO(String fileName, long size, java.time.LocalDateTime lastModified, boolean hasManifest) {
        super(fileName, size, lastModified, hasManifest);
    }
}

