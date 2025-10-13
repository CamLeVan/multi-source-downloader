package com.fragmented.download.core.client;

import java.util.concurrent.CompletableFuture;

public interface DownloadClient {
    CompletableFuture<byte[]> downloadPiece(String url, long startOffset, long endOffset);
}
