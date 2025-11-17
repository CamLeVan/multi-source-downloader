package com.fragmented.download.javafx.logic;

import com.fragmented.download.core.client.DownloadClient;
import com.fragmented.download.core.model.PieceModel;
import com.fragmented.download.networking.OkHttpDownloadClient;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Wrapper cho DownloadClient để track source đã download thành công
 */
public class SourceTrackingDownloadClient implements DownloadClient {
    
    private final OkHttpDownloadClient delegate;
    private Consumer<String> sourceTracker; // Callback để track source URL
    
    public SourceTrackingDownloadClient(OkHttpDownloadClient delegate) {
        this.delegate = delegate;
    }
    
    public void setSourceTracker(Consumer<String> sourceTracker) {
        this.sourceTracker = sourceTracker;
    }
    
    @Override
    public CompletableFuture<byte[]> downloadPiece(PieceModel piece) {
        CompletableFuture<byte[]> future = delegate.downloadPiece(piece);
        
        // Track source khi download thành công
        future.thenAccept(data -> {
            if (sourceTracker != null && piece.getSources() != null && !piece.getSources().isEmpty()) {
                // Giả định source đầu tiên đã download thành công
                // (Thực tế OkHttpDownloadClient đã thử từ source đầu tiên)
                String sourceUrl = piece.getSources().get(0);
                sourceTracker.accept(sourceUrl);
            }
        });
        
        return future;
    }
}

