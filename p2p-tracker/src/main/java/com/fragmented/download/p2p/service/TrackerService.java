package com.fragmented.download.p2p.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class TrackerService {

    private static final Logger log = LoggerFactory.getLogger(TrackerService.class);

    // Map<fileId, Set<ip:port>>
    private final ConcurrentHashMap<String, Set<String>> peers = new ConcurrentHashMap<>();

    public void announce(String fileId, String peerAddress) {
        // Use CopyOnWriteArraySet for thread-safe iteration and modification
        Set<String> peerSet = peers.computeIfAbsent(fileId, k -> new CopyOnWriteArraySet<>());
        peerSet.add(peerAddress);
        log.info("Announce from peer [{}] for fileId [{}].", peerAddress, fileId);
        log.debug("Current peers for fileId [{}]: {}", fileId, peerSet);
    }

    public Set<String> getPeers(String fileId) {
        Set<String> peerSet = peers.getOrDefault(fileId, Collections.emptySet());
        log.info("Peer lookup for fileId [{}]. Found {} peers.", fileId, peerSet.size());
        return peerSet;
    }

    /**
     * Get all file IDs being tracked
     * Added for admin panel access
     */
    public Set<String> getAllFileIds() {
        return new HashSet<>(peers.keySet());
    }

    /**
     * Get all peers map (for admin panel)
     * Added for admin panel access
     */
    public Map<String, Set<String>> getAllPeersMap() {
        return new HashMap<>(peers);
    }

    /**
     * Remove a peer from a file swarm
     * Added for admin panel
     */
    public boolean removePeer(String fileId, String peerAddress) {
        Set<String> peerSet = peers.get(fileId);
        if (peerSet != null) {
            boolean removed = peerSet.remove(peerAddress);
            if (peerSet.isEmpty()) {
                peers.remove(fileId);
            }
            log.info("Removed peer [{}] from fileId [{}].", peerAddress, fileId);
            return removed;
        }
        return false;
    }

    /**
     * Clear all peers for a file
     * Added for admin panel
     */
    public boolean clearFile(String fileId) {
        Set<String> removed = peers.remove(fileId);
        if (removed != null) {
            log.info("Cleared all peers for fileId [{}]. Removed {} peers.", fileId, removed.size());
            return true;
        }
        return false;
    }

    /**
     * Get total number of unique peers across all files
     * Added for admin panel
     */
    public int getTotalUniquePeers() {
        Set<String> uniquePeers = new HashSet<>();
        for (Set<String> peerSet : peers.values()) {
            uniquePeers.addAll(peerSet);
        }
        return uniquePeers.size();
    }
}
