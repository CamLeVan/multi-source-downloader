package com.fragmented.download.p2p.controller;

import com.fragmented.download.p2p.service.TrackerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*") // Allow frontend access
public class AdminController {

    @Autowired
    private TrackerService trackerService;
    
    // Mock logs storage
    private static final List<String> activityLogs = new CopyOnWriteArrayList<>();
    
    static {
        activityLogs.add("[SYSTEM] Tracker started successfully.");
        activityLogs.add("[SYSTEM] Waiting for peers...");
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> credentials) {
        Map<String, Object> response = new HashMap<>();
        String username = credentials.get("username");
        String password = credentials.get("password");
        
        if ("admin".equals(username) && "admin123".equals(password)) {
            response.put("success", true);
            response.put("token", "fake-jwt-token-123456");
        } else {
            response.put("success", false);
            response.put("message", "Invalid credentials");
        }
        return response;
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Use proper getter method instead of reflection
        Map<String, Set<String>> peersMap = trackerService.getAllPeersMap();
        
        stats.put("totalFiles", peersMap.size());
        
        Set<String> uniquePeers = new HashSet<>();
        List<Map<String, Object>> fileDetails = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : peersMap.entrySet()) {
            String fileId = entry.getKey();
            Set<String> peerList = entry.getValue();
            uniquePeers.addAll(peerList);

            Map<String, Object> fileStat = new HashMap<>();
            fileStat.put("name", fileId);
            fileStat.put("seeders", peerList.size());
            fileStat.put("peers", new ArrayList<>(peerList));
            fileDetails.add(fileStat);
        }
        
        stats.put("totalPeers", trackerService.getTotalUniquePeers());
        stats.put("activePeersList", new ArrayList<>(uniquePeers));
        stats.put("files", fileDetails);
        
        // Add mock traffic data for charts (can be replaced with real metrics later)
        stats.put("trafficIn", new Random().nextInt(100)); // Random MB/s
        stats.put("trafficOut", new Random().nextInt(100));
        stats.put("uptime", System.currentTimeMillis());
        stats.put("logs", new ArrayList<>(activityLogs));
        
        // Keep logs size manageable
        if (activityLogs.size() > 50) {
            activityLogs.remove(0);
        }
        
        return stats;
    }

    /**
     * Get detailed peer information for a specific file
     */
    @GetMapping("/files/{fileId}/peers")
    public Map<String, Object> getFilePeers(@PathVariable String fileId) {
        Map<String, Object> result = new HashMap<>();
        Set<String> peers = trackerService.getPeers(fileId);
        
        result.put("fileId", fileId);
        result.put("peerCount", peers.size());
        result.put("peers", new ArrayList<>(peers));
        
        return result;
    }

    /**
     * Remove a peer from a file swarm
     */
    @DeleteMapping("/files/{fileId}/peers/{peerAddress}")
    public Map<String, Object> removePeer(@PathVariable String fileId, @PathVariable String peerAddress) {
        Map<String, Object> result = new HashMap<>();
        boolean removed = trackerService.removePeer(fileId, peerAddress);
        
        result.put("success", removed);
        result.put("message", removed ? "Peer removed successfully" : "Peer not found");
        
        if (removed) {
            addLog("Removed peer " + peerAddress + " from file " + fileId);
        }
        
        return result;
    }

    /**
     * Clear all peers for a file
     */
    @DeleteMapping("/files/{fileId}")
    public Map<String, Object> clearFile(@PathVariable String fileId) {
        Map<String, Object> result = new HashMap<>();
        boolean cleared = trackerService.clearFile(fileId);
        
        result.put("success", cleared);
        result.put("message", cleared ? "File cleared successfully" : "File not found");
        
        if (cleared) {
            addLog("Cleared all peers for file " + fileId);
        }
        
        return result;
    }
    
    // Helper to add logs (call this from TrackerService if possible, or mock it here)
    public static void addLog(String message) {
        activityLogs.add("[" + new Date() + "] " + message);
    }
}
