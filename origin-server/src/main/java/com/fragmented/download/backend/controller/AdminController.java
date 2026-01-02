package com.fragmented.download.backend.controller;

import com.fragmented.download.backend.config.FileServerInitializer;
import com.fragmented.download.core.model.FileInfoDTO;
import com.fragmented.download.backend.util.ManifestGeneratorUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin Controller for Origin Server
 * Provides admin APIs for file management, statistics, and monitoring
 * NOTE: This is separate from ManifestController to avoid affecting existing logic
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    /**
     * Get server statistics and overview
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(HttpServletRequest request) {
        String clientIP = request.getRemoteAddr();
        System.out.println(String.format("[%s] [ADMIN] GET /api/admin/stats | FROM: %s",
                LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                clientIP));

        try {
            Path serverDir = Paths.get(FileServerInitializer.SERVER_FILE_DIR);
            File[] files = serverDir.toFile().listFiles();

            long totalSize = 0;
            int fileCount = 0;
            int manifestCount = 0;

            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        if (file.getName().endsWith(".manifest.json")) {
                            manifestCount++;
                        } else {
                            fileCount++;
                            totalSize += file.length();
                        }
                    }
                }
            }

            // Get disk usage
            long freeSpace = serverDir.toFile().getFreeSpace();
            long totalSpace = serverDir.toFile().getTotalSpace();
            long usedSpace = totalSpace - freeSpace;

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalFiles", fileCount);
            stats.put("totalManifests", manifestCount);
            stats.put("totalSize", totalSize);
            stats.put("totalSizeFormatted", formatBytes(totalSize));
            stats.put("diskFree", freeSpace);
            stats.put("diskTotal", totalSpace);
            stats.put("diskUsed", usedSpace);
            stats.put("diskFreeFormatted", formatBytes(freeSpace));
            stats.put("diskTotalFormatted", formatBytes(totalSpace));
            stats.put("diskUsedFormatted", formatBytes(usedSpace));
            stats.put("diskUsagePercent", totalSpace > 0 ? (usedSpace * 100.0 / totalSpace) : 0);

            System.out.println(String.format("[%s] [ADMIN] ✓ Stats sent | FROM: Origin → TO: %s",
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                    clientIP));

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            System.err.println(String.format("[%s] [ADMIN] ✗ Error getting stats | FROM: %s | Error: %s",
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                    clientIP, e.getMessage()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get detailed file list with admin info
     */
    @GetMapping("/files")
    public ResponseEntity<List<FileInfoDTO>> getFiles(HttpServletRequest request) {
        String clientIP = request.getRemoteAddr();
        System.out.println(String.format("[%s] [ADMIN] GET /api/admin/files | FROM: %s",
                LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                clientIP));

        try {
            Path serverDir = Paths.get(FileServerInitializer.SERVER_FILE_DIR);
            List<FileInfoDTO> fileInfos = new ArrayList<>();

            try (java.util.stream.Stream<Path> paths = Files.walk(serverDir, 1)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> !p.getFileName().toString().endsWith(".manifest.json"))
                     .forEach(p -> {
                         try {
                             String fileName = p.getFileName().toString();
                             long size = Files.size(p);
                             LocalDateTime lastModified = LocalDateTime.ofInstant(
                                 Files.getLastModifiedTime(p).toInstant(),
                                 java.time.ZoneId.systemDefault()
                             );
                             
                             Path manifestPath = Paths.get(FileServerInitializer.SERVER_FILE_DIR, fileName + ".manifest.json");
                             boolean hasManifest = Files.exists(manifestPath);
                             
                             FileInfoDTO fileInfo = new FileInfoDTO(fileName, size, lastModified, hasManifest);
                             fileInfos.add(fileInfo);
                         } catch (IOException e) {
                             System.err.println("Error reading file info: " + p + " - " + e.getMessage());
                         }
                     });
            }

            System.out.println(String.format("[%s] [ADMIN] ✓ Files list sent | FROM: Origin → TO: %s | Count: %d",
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                    clientIP, fileInfos.size()));

            return ResponseEntity.ok(fileInfos);
        } catch (IOException e) {
            System.err.println(String.format("[%s] [ADMIN] ✗ Error listing files | FROM: %s | Error: %s",
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                    clientIP, e.getMessage()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete a file and its manifest
     */
    @DeleteMapping("/files/{fileName}")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable String fileName, HttpServletRequest request) {
        String clientIP = request.getRemoteAddr();
        System.out.println(String.format("[%s] [ADMIN] DELETE /api/admin/files/%s | FROM: %s",
                LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                fileName, clientIP));

        try {
            Path filePath = Paths.get(FileServerInitializer.SERVER_FILE_DIR, fileName);
            Path manifestPath = Paths.get(FileServerInitializer.SERVER_FILE_DIR, fileName + ".manifest.json");

            Map<String, Object> result = new HashMap<>();
            boolean fileDeleted = false;
            boolean manifestDeleted = false;

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                fileDeleted = true;
            }

            if (Files.exists(manifestPath)) {
                Files.delete(manifestPath);
                manifestDeleted = true;
            }

            if (!fileDeleted && !manifestDeleted) {
                result.put("success", false);
                result.put("message", "File not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
            }

            result.put("success", true);
            result.put("fileDeleted", fileDeleted);
            result.put("manifestDeleted", manifestDeleted);
            result.put("message", "File deleted successfully");

            System.out.println(String.format("[%s] [ADMIN] ✓ File deleted | FROM: Origin → TO: %s | File: %s",
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                    clientIP, fileName));

            return ResponseEntity.ok(result);
        } catch (IOException e) {
            System.err.println(String.format("[%s] [ADMIN] ✗ Error deleting file | FROM: %s | Error: %s",
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                    clientIP, e.getMessage()));

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error deleting file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Upload a new file
     */
    @PostMapping("/files/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "generateManifest", defaultValue = "true") boolean generateManifest,
            HttpServletRequest request) {
        String clientIP = request.getRemoteAddr();
        System.out.println(String.format("[%s] [ADMIN] POST /api/admin/files/upload | FROM: %s | File: %s",
                LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                clientIP, file.getOriginalFilename()));

        try {
            if (file.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "File is empty");
                return ResponseEntity.badRequest().body(error);
            }

            String fileName = file.getOriginalFilename();
            if (fileName == null || fileName.isEmpty()) {
                fileName = "uploaded_file_" + System.currentTimeMillis();
            }

            Path targetPath = Paths.get(FileServerInitializer.SERVER_FILE_DIR, fileName);
            
            // Create directory if not exists
            Files.createDirectories(targetPath.getParent());

            // Save file
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("fileName", fileName);
            result.put("size", file.getSize());
            result.put("sizeFormatted", formatBytes(file.getSize()));

            // Generate manifest if requested
            if (generateManifest) {
                try {
                    ManifestGeneratorUtil.generateManifest(targetPath.toFile());
                    result.put("manifestGenerated", true);
                } catch (Exception e) {
                    System.err.println("Error generating manifest: " + e.getMessage());
                    result.put("manifestGenerated", false);
                    result.put("manifestError", e.getMessage());
                }
            } else {
                result.put("manifestGenerated", false);
            }

            System.out.println(String.format("[%s] [ADMIN] ✓ File uploaded | FROM: Origin → TO: %s | File: %s",
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                    clientIP, fileName));

            return ResponseEntity.ok(result);
        } catch (IOException e) {
            System.err.println(String.format("[%s] [ADMIN] ✗ Error uploading file | FROM: %s | Error: %s",
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                    clientIP, e.getMessage()));

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error uploading file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Regenerate manifest for a file
     */
    @PostMapping("/files/{fileName}/regenerate-manifest")
    public ResponseEntity<Map<String, Object>> regenerateManifest(@PathVariable String fileName, HttpServletRequest request) {
        String clientIP = request.getRemoteAddr();
        System.out.println(String.format("[%s] [ADMIN] POST /api/admin/files/%s/regenerate-manifest | FROM: %s",
                LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                fileName, clientIP));

        try {
            Path filePath = Paths.get(FileServerInitializer.SERVER_FILE_DIR, fileName);
            
            if (!Files.exists(filePath)) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "File not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            ManifestGeneratorUtil.generateManifest(filePath.toFile());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Manifest regenerated successfully");
            result.put("fileName", fileName);

            System.out.println(String.format("[%s] [ADMIN] ✓ Manifest regenerated | FROM: Origin → TO: %s | File: %s",
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                    clientIP, fileName));

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.err.println(String.format("[%s] [ADMIN] ✗ Error regenerating manifest | FROM: %s | Error: %s",
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                    clientIP, e.getMessage()));

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error regenerating manifest: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

