package com.fragmented.download.core.logic;

import com.fragmented.download.core.model.NetworkMetrics;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Singleton Logger to handle network metrics logging asynchronously.
 * This prevents I/O blocking on download threads and avoids race conditions
 * when multiple clients are writing to the same CSV file.
 */
public class AsyncNetworkLogger {

    private static final AsyncNetworkLogger INSTANCE = new AsyncNetworkLogger();
    private static final String CSV_FILE = "network_data.csv";

    // Thread-safe queue to hold log entries
    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();

    private AsyncNetworkLogger() {
        initCsvHeader();
        startWriterThread();
    }

    public static AsyncNetworkLogger getInstance() {
        return INSTANCE;
    }

    /**
     * Non-blocking log method. Adds the metric to the memory queue and returns
     * immediately.
     */
    public void log(NetworkMetrics metrics, String label) {
        // Format: responseTimeMs,speedKBps,isSuccess,label
        String logLine = String.format("%d,%.2f,%b,%s",
                metrics.getResponseTimeMs(),
                metrics.getDownloadSpeedKBps(),
                metrics.isSuccess(),
                label);

        logQueue.offer(logLine);
    }

    private void initCsvHeader() {
        // Only write header if file is empty or doesn't exist
        java.io.File file = new java.io.File(CSV_FILE);
        if (!file.exists() || file.length() == 0) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE, true))) {
                writer.println("responseTimeMs,speedKBps,isSuccess,label");
            } catch (IOException e) {
                System.err.println("[AsyncNetworkLogger] Failed to init CSV header: " + e.getMessage());
            }
        }
    }

    private void startWriterThread() {
        Thread writerThread = new Thread(() -> {
            try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE, true))) {
                while (true) {
                    try {
                        // Take from queue (blocks if empty)
                        String logEntry = logQueue.take();
                        writer.println(logEntry);

                        // Flux optimization:
                        // If queue is empty, flush immediately to ensure data is on disk.
                        // If queue has more items, keep writing and flush later (or let OS handle it).
                        // For safety in this project, we flush often to avoid data loss on crash.
                        if (logQueue.isEmpty()) {
                            writer.flush();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("[AsyncNetworkLogger] Writer thread IO Error: " + e.getMessage());
            }
        });

        writerThread.setDaemon(true); // Ensure thread dies when app closes
        writerThread.setName("Async-CSV-Writer");
        writerThread.start();
    }
}
