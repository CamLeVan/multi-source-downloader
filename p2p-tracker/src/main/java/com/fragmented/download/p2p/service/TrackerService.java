package com.fragmented.download.p2p.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
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
}
