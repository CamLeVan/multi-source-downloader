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
        String peerAddress = request.getRemoteAddr() + ":" + announceRequest.getPort();
        trackerService.announce(announceRequest.getFileId(), peerAddress);
        return ResponseEntity.ok().build();
    }

    /**
     * A peer requests the list of other peers for a specific file.
     * @param fileId The ID of the file.
     * @return A set of strings, where each string is an "ip:port" address of a peer.
     */
    @GetMapping("/peers")
    public ResponseEntity<Set<String>> getPeers(@RequestParam String fileId) {
        Set<String> peers = trackerService.getPeers(fileId);
        return ResponseEntity.ok(peers);
    }
}
