package com.fragmented.download.client;

import com.fragmented.download.core.client.DownloadClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class OkHttpDownloadClientTest {

    private MockWebServer mockWebServer;
    private DownloadClient downloadClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        downloadClient = new OkHttpDownloadClient();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void downloadPiece_sendsCorrectRangeHeader_andReceivesData() throws InterruptedException, ExecutionException {
        // 1. Mock Server setup
        byte[] mockData = new byte[1024];
        Arrays.fill(mockData, (byte) 'a'); // Fill with some data

        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes 0-1023/2048")
                        .setBody(new okio.Buffer().write(mockData))
        );

        String url = mockWebServer.url("/testfile").toString();

        // 2. Call the method under test
        CompletableFuture<byte[]> future = downloadClient.downloadPiece(url, 0, 1023);

        // 3. Assertions
        // Assert that the request contains the correct Range header
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("bytes=0-1023", recordedRequest.getHeader("Range"));

        // Assert that the CompletableFuture completes successfully with the correct data
        byte[] result = future.get();
        assertNotNull(result);
        assertEquals(1024, result.length);
        assertArrayEquals(mockData, result);
    }

    @Test
    void downloadPiece_whenServerReturnsError_throwsException() {
        // Mock server to return an error status
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        String url = mockWebServer.url("/testfile").toString();

        // Call the method
        CompletableFuture<byte[]> future = downloadClient.downloadPiece(url, 0, 1023);

        // Assert that the future completes exceptionally
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);

        // Check the cause of the exception
        assertTrue(exception.getCause() instanceof IOException);
        assertEquals("Unexpected code 500", exception.getCause().getMessage());
    }
}
