package com.fragmented.download.client;

import com.fragmented.download.core.client.DownloadClient;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class OkHttpDownloadClient implements DownloadClient {

    private final OkHttpClient client;

    public OkHttpDownloadClient() {
        this.client = new OkHttpClient();
    }

    public OkHttpDownloadClient(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<byte[]> downloadPiece(String url, long startOffset, long endOffset) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();

        Request request = new Request.Builder()
                .url(url)
                .header("Range", "bytes=" + startOffset + "-" + endOffset)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.code() != 200 && response.code() != 206) {
                    future.completeExceptionally(new IOException("Unexpected code " + response));
                    return;
                }

                try (ResponseBody body = response.body()) {
                    if (body != null) {
                        future.complete(body.bytes());
                    } else {
                        future.completeExceptionally(new IOException("Response body is null"));
                    }
                }
            }
        });

        return future;
    }
}
