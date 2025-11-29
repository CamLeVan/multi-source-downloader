# Multi-Source Fragmented File Download System & Virtual File Access
## Network Programming Project Presentation

---

# Slide 1: Title & Introduction

**Project Title:**
Building a Multi-Source Fragmented File Download System and Virtual File Access using HTTP Range Protocol and Hybrid Client-Server/P2P Model.

**Team Members:**
*   Pham Mai Gia Huy (23IT.B081)
*   Le Van Cam (23IT.B016)

**Instructor:**
*   M.Sc. Nguyen Thanh Cam

**University:**
Vietnam-Korea University of Information and Communication Technology (VKU)
*Faculty of Computer Science*

---

# Slide 2: Rationale (Problem Statement)

**The Challenge:**
*   **Big Data Era:** Files are getting massive (4K/8K videos, games, datasets > 20GB).
*   **Traditional Downloading (Single Source):**
    *   **Slow Speed:** Limited by the server's bandwidth.
    *   **Low Reliability:** Single point of failure. Connection lost = Restart from 0.
    *   **Inefficient:** Must download 100% to use the file (cannot stream instantly).

**Our Solution:**
A **Multi-Source Downloader** that combines the reliability of Client-Server with the speed and scalability of P2P.

---

# Slide 3: Objectives & Scope

**Core Objectives:**
1.  **High Speed:** Download from multiple sources simultaneously (Origin, Mirrors, Peers).
2.  **Reliability:** Auto-recovery from failures (Fallback mechanism).
3.  **Instant Access:** "Play as you download" via Virtual Filesystem (On-demand access).

**Scope:**
*   **Application Layer:** Custom protocol built on top of HTTP.
*   **Transport Layer:** Uses TCP for reliable data transfer.
*   **Hybrid Model:**
    *   **Client-Server:** For initial reliable source (Manifest).
    *   **P2P:** For sharing pieces between users to reduce server load.

---

# Slide 4: Theoretical Basis - HTTP Range Requests

**The Key Technology: RFC 7233**
*   Standard HTTP downloads the whole file (Status 200 OK).
*   **HTTP Range Requests** allow downloading specific byte ranges.

**Mechanism:**
1.  **Request:** `GET /file.mp4` with Header `Range: bytes=0-1048575` (Requesting the first 1MB).
2.  **Response:** Server returns Status `206 Partial Content` with only that 1MB chunk.

**Application:**
*   We split a 20GB file into thousands of **1MB Pieces**.
*   Each piece is downloaded independently and in parallel.

---

# Slide 5: System Architecture (Hybrid Model)

**Components:**
1.  **Origin Server (Spring Boot):**
    *   The "Source of Truth".
    *   Provides the **Manifest** (Metadata, Hash list).
    *   Handles HTTP Range requests.
2.  **P2P Tracker:**
    *   Helps peers find each other.
    *   Manages the "Swarm" of active downloaders.
3.  **Client (JavaFX):**
    *   **Downloader:** Fetches pieces from Origin, Mirrors, and Peers.
    *   **Uploader (Servent):** Shares downloaded pieces with others.

---

# Slide 6: Key Algorithms - The Scheduler

**Client-Side Logic:**
1.  **Manifest Parsing:** Read file size and piece hashes.
2.  **Task Scheduling:**
    *   Maintain a `PieceQueue` of missing pieces.
    *   **Multi-threading:** Use a Thread Pool (ExecutorService) to run multiple `DownloadWorkers`.
3.  **Dynamic Source Selection:**
    *   Worker picks the best available source.
    *   **Validation:** Calculate SHA-256 of downloaded data.
    *   **Retry:** If Hash Mismatch -> Discard and retry from another source.

---

# Slide 7: Implementation - Technology Stack

**Language:** Java 21 (Strong concurrency support).

**Frameworks & Libraries:**
*   **Server:** Spring Boot 3.1.5 (REST API).
*   **Client UI:** JavaFX (Modern Desktop GUI).
*   **Networking:** OkHttp (Efficient HTTP Client).
*   **Virtual FS:** jnr-fuse (Mounting virtual drives for on-demand access).

**Why Java?**
*   Robust Multi-threading (`java.util.concurrent`).
*   Cross-platform consistency.

---

# Slide 8: Experimental Results

**Test Scenario:** Downloading a 5GB File on LAN.

| Scenario | Configuration | Time | Speed |
| :--- | :--- | :--- | :--- |
| **1. Traditional** | Single Source (Origin) | 4m 15s | ~19.6 MB/s |
| **2. Multi-Source** | 3 Sources (1 Origin + 2 Mirrors) | **1m 28s** | **~56.8 MB/s** |

**Reliability Test:**
*   Simulated a Mirror Server crash during download.
*   **Result:** System automatically detected failure, re-routed requests to remaining servers. **Zero interruption.**

---

# Slide 9: Conclusion & Future Work

**Achievements:**
*   Successfully built a Hybrid Downloader.
*   Implemented HTTP Range & SHA-256 Integrity Check.
*   Integrated Virtual Filesystem for instant media playback.

**Future Improvements:**
1.  **AI Anomaly Detection:**
    *   Use Machine Learning (Isolation Forest) to detect slow/malicious peers *before* the download finishes.
    *   Proactive instead of Reactive fallback.
2.  **Native Packaging:**
    *   Distribute as `.exe` or `.dmg` for end-users.

---

# Slide 10: Q&A

**Thank You!**

*Questions?*
