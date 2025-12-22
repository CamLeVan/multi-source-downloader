package com.fragmented.download.core.logic;

import com.fragmented.download.core.model.NetworkMetrics;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;

/**
 * SmartDetector: A rule-based anomaly detector for network connections.
 * This acts as the "Supervisor" to identify poor connections.
 * 
 * UPDATE: Added Data Collection Mode to log metrics to CSV for AI training.
 */
public class SmartDetector {

    // Thresholds (can be tuned or learned later)
    private static final long MAX_LATENCY_MS = 2000; // 2 seconds
    private static final double MIN_SPEED_KBPS = 50.0; // 50 KB/s
    
    private static final String CSV_FILE = "network_data.csv";
    
    public SmartDetector() {
        // Initialize CSV with header if not exists
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE, true))) {
            java.io.File file = new java.io.File(CSV_FILE);
            if (file.length() == 0) {
                writer.println("responseTimeMs,speedKBps,isSuccess,label");
            }
        } catch (IOException e) {
            System.err.println("Failed to init CSV: " + e.getMessage());
        }
    }
    
    /**
     * Evaluates if the current network metrics indicate an anomaly (poor connection).
     * 
     * @param metrics The collected network metrics.
     * @return true if an anomaly is detected, false otherwise.
     */
    public boolean isAnomaly(NetworkMetrics metrics) {
        boolean isAnomaly = false;
        
        if (!metrics.isSuccess()) {
            isAnomaly = true; // Failed connection is definitely an anomaly
        } else if (metrics.getResponseTimeMs() > MAX_LATENCY_MS) {
            System.out.println("⚠ Anomaly Detected: High Latency (" + metrics.getResponseTimeMs() + "ms)");
            isAnomaly = true;
        } else if (metrics.getDownloadSpeedKBps() < MIN_SPEED_KBPS) {
            System.out.println("⚠ Anomaly Detected: Low Speed (" + metrics.getDownloadSpeedKBps() + " KB/s)");
            isAnomaly = true;
        }

        // Log to CSV for training
        logToCsv(metrics, isAnomaly ? "ANOMALY" : "NORMAL");

        return isAnomaly;
    }

    private void logToCsv(NetworkMetrics metrics, String label) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE, true))) {
            writer.printf("%d,%.2f,%b,%s%n", 
                metrics.getResponseTimeMs(), 
                metrics.getDownloadSpeedKBps(), 
                metrics.isSuccess(), 
                label);
        } catch (IOException e) {
            System.err.println("Failed to write to CSV: " + e.getMessage());
        }
    }
}
