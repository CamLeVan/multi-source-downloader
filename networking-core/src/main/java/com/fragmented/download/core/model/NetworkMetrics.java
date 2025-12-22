package com.fragmented.download.core.model;

/**
 * Stores network performance metrics for a single download operation.
 * Used for anomaly detection.
 */
public class NetworkMetrics {
    private final long responseTimeMs; // Time to first byte or total download time
    private final double downloadSpeedKBps; // Download speed in KB/s
    private final double variance; // Jitter or variance in byte reception (simplified)
    private final boolean isSuccess;

    public NetworkMetrics(long responseTimeMs, double downloadSpeedKBps, double variance, boolean isSuccess) {
        this.responseTimeMs = responseTimeMs;
        this.downloadSpeedKBps = downloadSpeedKBps;
        this.variance = variance;
        this.isSuccess = isSuccess;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public double getDownloadSpeedKBps() {
        return downloadSpeedKBps;
    }

    public double getVariance() {
        return variance;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    @Override
    public String toString() {
        return String.format("Metrics[Time=%dms, Speed=%.1fKB/s, Var=%.2f, Success=%b]", 
            responseTimeMs, downloadSpeedKBps, variance, isSuccess);
    }
}
