package com.fragmented.download.p2p.controller;

import com.fragmented.download.p2p.dto.AnnounceRequest;
import com.fragmented.download.p2p.service.TrackerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/tracker") // Base path for the tracker
public class TrackerController {

    private final TrackerService trackerService;

    // Constructor injection is a best practice
    public TrackerController(TrackerService trackerService) {
        this.trackerService = trackerService;
    }

    /**
     * A peer announces its presence for a specific file.
     * @param announceRequest DTO containing fileId, peerId, and port.
     * @param request The incoming HTTP request to get the peer's IP address.
     * @return HTTP 200 OK.
     */
    @PostMapping("/announce")
    public ResponseEntity<Void> announce(@RequestBody AnnounceRequest announceRequest, HttpServletRequest request) {
        // The map stores ip:port as requested.
        String peerIP = request.getRemoteAddr();
        String peerAddress = peerIP + ":" + announceRequest.getPort();
        
        System.out.println(String.format("[%s] [TRACKER] POST /announce | FROM: %s → TO: Tracker:8081 | fileId=%s, port=%d", 
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
            peerIP, announceRequest.getFileId(), announceRequest.getPort()));
        
        trackerService.announce(announceRequest.getFileId(), peerAddress);
        
        System.out.println(String.format("[%s] [TRACKER] ✓ Announce registered | FROM: Tracker:8081 → TO: %s | Peer: %s", 
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
            peerIP, peerAddress));
        
        return ResponseEntity.ok().build();
    }

    /**
     * A peer requests the list of other peers for a specific file.
     * @param fileId The ID of the file.
     * @return A set of strings, where each string is an "ip:port" address of a peer.
     */
    @GetMapping("/peers")
    public ResponseEntity<Set<String>> getPeers(@RequestParam String fileId, HttpServletRequest request) {
        String clientIP = request.getRemoteAddr();
        
        System.out.println(String.format("[%s] [TRACKER] GET /peers?fileId=%s | FROM: %s → TO: Tracker:8081", 
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
            fileId, clientIP));
        
        Set<String> peers = trackerService.getPeers(fileId);
        
        System.out.println(String.format("[%s] [TRACKER] ✓ Peers list sent | FROM: Tracker:8081 → TO: %s | Count: %d", 
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
            clientIP, peers.size()));
        
        return ResponseEntity.ok(peers);
    }
}
