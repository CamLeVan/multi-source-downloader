package com.fragmented.download.p2p.controller;

import com.fragmented.download.p2p.service.TrackerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
        
        // Hack: Access private field 'peers' via Reflection because TrackerService doesn't expose it
        // In a real project, we should add a getter to TrackerService.
        Map<String, Set<String>> peersMap = getPeersMapFromService();
        
        if (peersMap != null) {
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
                fileStat.put("peers", peerList);
                fileDetails.add(fileStat);
            }
            
            stats.put("totalPeers", uniquePeers.size());
            stats.put("activePeersList", uniquePeers);
            stats.put("files", fileDetails);
            
            // Add mock traffic data for charts
            stats.put("trafficIn", new Random().nextInt(100)); // Random MB/s
            stats.put("trafficOut", new Random().nextInt(100));
            
        } else {
            stats.put("error", "Could not access tracker data");
        }
        
        stats.put("uptime", System.currentTimeMillis());
        stats.put("logs", activityLogs);
        
        // Keep logs size manageable
        if (activityLogs.size() > 50) {
            activityLogs.remove(0);
        }
        
        return stats;
    }
    
    // Helper to add logs (call this from TrackerService if possible, or mock it here)
    public static void addLog(String message) {
        activityLogs.add("[" + new Date() + "] " + message);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> getPeersMapFromService() {
        try {
            Field field = TrackerService.class.getDeclaredField("peers");
            field.setAccessible(true);
            return (ConcurrentHashMap<String, Set<String>>) field.get(trackerService);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
