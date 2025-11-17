package com.fragmented.download.javafx.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logger để hiển thị flow hoạt động với IP addresses
 */
public class FlowLogger {
    
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final boolean SHOW_FLOW = ConfigManager.getBoolean("log.show.flow", true);
    private static final boolean SHOW_IP = ConfigManager.getBoolean("log.show.ip", true);
    
    private static String getTimestamp() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }
    
    private static String formatIP(String ip, int port) {
        if (SHOW_IP) {
            return ip + ":" + port;
        }
        return "***:***";
    }
    
    private static String formatIP(String address) {
        if (SHOW_IP) {
            return address;
        }
        return "***:***";
    }
    
    public static void logStep(int step, String action, String fromIP, String toIP) {
        if (!SHOW_FLOW) return;
        
        String from = fromIP != null ? formatIP(fromIP) : "N/A";
        String to = toIP != null ? formatIP(toIP) : "N/A";
        
        System.out.println(String.format("[%s] [STEP %02d] %s | FROM: %s → TO: %s", 
            getTimestamp(), step, action, from, to));
    }
    
    public static void logStep(int step, String action, String fromIP, int fromPort, String toIP, int toPort) {
        if (!SHOW_FLOW) return;
        
        String from = fromIP != null ? formatIP(fromIP, fromPort) : "N/A";
        String to = toIP != null ? formatIP(toIP, toPort) : "N/A";
        
        System.out.println(String.format("[%s] [STEP %02d] %s | FROM: %s → TO: %s", 
            getTimestamp(), step, action, from, to));
    }
    
    public static void logInfo(String message, String ip) {
        if (!SHOW_FLOW) return;
        System.out.println(String.format("[%s] [INFO] %s | IP: %s", 
            getTimestamp(), message, SHOW_IP ? ip : "***"));
    }
    
    public static void logError(String message, String ip, String error) {
        System.err.println(String.format("[%s] [ERROR] %s | IP: %s | Error: %s", 
            getTimestamp(), message, SHOW_IP ? ip : "***", error));
    }
    
    public static void logSeparator() {
        if (!SHOW_FLOW) return;
        System.out.println("═══════════════════════════════════════════════════════════════");
    }
    
    public static void logSection(String sectionName) {
        if (!SHOW_FLOW) return;
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║ " + String.format("%-59s", sectionName) + "║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝");
    }
}

