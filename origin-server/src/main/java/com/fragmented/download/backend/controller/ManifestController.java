package com.fragmented.download.backend.controller;

import com.fragmented.download.backend.config.FileServerInitializer;
import com.fragmented.download.core.model.FileInfoDTO;
import com.fragmented.download.core.model.ManifestModel;
import com.google.gson.Gson;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class ManifestController {

    private final Gson gson = new Gson();

    /**
     * API để lấy metadata của tất cả files (Phase 1.2: Enhanced API với metadata)
     * Trả về thông tin đầy đủ: fileName, size, lastModified, mirrorsCount, hasManifest
     */
    @GetMapping("/files/info")
    public ResponseEntity<java.util.List<FileInfoDTO>> getFilesInfo(jakarta.servlet.http.HttpServletRequest request) {
        String clientIP = request.getRemoteAddr();
        System.out.println(String.format("[%s] [ORIGIN] GET /files/info | FROM: %s → TO: Origin", 
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
            clientIP));
        
        try {
            Path serverDir = Paths.get(FileServerInitializer.SERVER_FILE_DIR);
            java.util.List<FileInfoDTO> fileInfos = new java.util.ArrayList<>();
            
            // Scan thư mục và lấy metadata của mỗi file
            try (java.util.stream.Stream<Path> paths = Files.walk(serverDir, 1)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> !p.getFileName().toString().endsWith(".manifest.json"))
                     .forEach(p -> {
                         try {
                             String fileName = p.getFileName().toString();
                             long size = Files.size(p);
                             java.time.LocalDateTime lastModified = java.time.LocalDateTime.ofInstant(
                                 Files.getLastModifiedTime(p).toInstant(),
                                 java.time.ZoneId.systemDefault()
                             );
                             
                             // Check if manifest exists
                             Path manifestPath = Paths.get(FileServerInitializer.SERVER_FILE_DIR, fileName + ".manifest.json");
                             boolean hasManifest = Files.exists(manifestPath);
                             
                             FileInfoDTO fileInfo = new FileInfoDTO(fileName, size, lastModified, hasManifest);
                             fileInfos.add(fileInfo);
                         } catch (IOException e) {
                             System.err.println("Error reading file info: " + p + " - " + e.getMessage());
                         }
                     });
            }
            
            System.out.println(String.format("[%s] [ORIGIN] ✓ File info sent | FROM: Origin → TO: %s | Count: %d", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                clientIP, fileInfos.size()));
            
            return ResponseEntity.ok(fileInfos);
        } catch (IOException e) {
            System.err.println(String.format("[%s] [ORIGIN] ✗ Error listing files info | FROM: %s | Error: %s", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                clientIP, e.getMessage()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * API để list tất cả files có sẵn trên server (Legacy - giữ lại để backward compatibility).
     * Client dùng để hiển thị danh sách files có thể download.
     */
    @GetMapping("/files/list")
    public ResponseEntity<java.util.List<String>> listAvailableFiles(jakarta.servlet.http.HttpServletRequest request) {
        String clientIP = request.getRemoteAddr();
        System.out.println(String.format("[%s] [ORIGIN] GET /files/list | FROM: %s → TO: Origin", 
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
            clientIP));
        
        try {
            Path serverDir = Paths.get(FileServerInitializer.SERVER_FILE_DIR);
            java.util.List<String> fileNames = new java.util.ArrayList<>();
            
            // Scan thư mục và lấy tất cả files (trừ .manifest.json)
            try (java.util.stream.Stream<Path> paths = Files.walk(serverDir, 1)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> !p.getFileName().toString().endsWith(".manifest.json"))
                     .forEach(p -> fileNames.add(p.getFileName().toString()));
            }
            
            System.out.println(String.format("[%s] [ORIGIN] ✓ File list sent | FROM: Origin → TO: %s | Count: %d", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                clientIP, fileNames.size()));
            
            return ResponseEntity.ok(fileNames);
        } catch (IOException e) {
            System.err.println(String.format("[%s] [ORIGIN] ✗ Error listing files | FROM: %s | Error: %s", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                clientIP, e.getMessage()));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Phục vụ file Manifest (JSON) cho client.
     * Task Tuần 1.
     */
    @GetMapping("/manifest/{fileName}")
    public ManifestModel getManifest(@PathVariable String fileName, jakarta.servlet.http.HttpServletRequest request) throws FileNotFoundException {
        String clientIP = request.getRemoteAddr();
        System.out.println(String.format("[%s] [ORIGIN] GET /manifest/%s | FROM: %s → TO: Origin:8443", 
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
            fileName, clientIP));
        
        String manifestFileName = fileName + ".manifest.json";
        Path manifestPath = Paths.get(FileServerInitializer.SERVER_FILE_DIR, manifestFileName);

        if (!Files.exists(manifestPath)) {
            System.err.println(String.format("[%s] [ORIGIN] ✗ Manifest not found: %s | FROM: %s", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                fileName, clientIP));
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Manifest file not found");
        }

        try (FileReader reader = new FileReader(manifestPath.toFile())) {
            ManifestModel manifest = gson.fromJson(reader, ManifestModel.class);
            System.out.println(String.format("[%s] [ORIGIN] ✓ Manifest sent | FROM: Origin:8443 → TO: %s | Pieces: %d", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                clientIP, manifest.getPieces().size()));
            return manifest;
        } catch (IOException e) {
            System.err.println(String.format("[%s] [ORIGIN] ✗ Error reading manifest | FROM: %s | Error: %s", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                clientIP, e.getMessage()));
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading manifest file", e);
        }
    }

    /**
     * Phục vụ các mảnh file (pieces) qua HTTP Range (Streaming I/O).
     * Task Tuần 3.
     */
    @GetMapping("/files/{fileName}")
    public ResponseEntity<InputStreamResource> getFilePiece(
            @PathVariable String fileName,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            jakarta.servlet.http.HttpServletRequest request)
            throws IOException {
        String clientIP = request.getRemoteAddr();
        if (rangeHeader != null) {
            System.out.println(String.format("[%s] [ORIGIN] GET /files/%s | Range: %s | FROM: %s → TO: Origin:8443", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                fileName, rangeHeader, clientIP));
        }

        Path filePath = Paths.get(FileServerInitializer.SERVER_FILE_DIR, fileName);
        File file = filePath.toFile();
        long fileLength = file.length();

        if (!file.exists()) {
             throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }

        if (rangeHeader == null) {
            // Trả về toàn bộ file nếu không yêu cầu Range
            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentLength(fileLength)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        }

        // Parse the range header
        String[] ranges = rangeHeader.substring("bytes=".length()).split("-");
        long start = Long.parseLong(ranges[0]);
        long end;
        if (ranges.length > 1 && !ranges[1].trim().isEmpty()) {
            end = Long.parseLong(ranges[1]);
        } else {
            end = fileLength - 1;
        }


        // *** ĐÃ SỬA LỖI ***
        // Thêm khối xác thực (validation) từ DownloadController (đã xóa)
        if (start < 0 || start >= fileLength || end < start || end >= fileLength) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLength)
                    .build();
        }
        // *** KẾT THÚC SỬA LỖI ***

        long contentLength = (end - start) + 1;

        // Sử dụng Streaming I/O hiệu suất cao
        InputStream inputStream = Files.newInputStream(filePath);
        inputStream.skip(start);

        InputStreamResource resource = new InputStreamResource(new LimitedInputStream(inputStream, contentLength));

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength)
                .contentLength(contentLength)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * Lớp helper để giới hạn InputStream chỉ đọc đúng số byte yêu cầu.
     * Giống hệt BoundedInputStream trong ManifestGeneratorUtil.
     */
    private static class LimitedInputStream extends InputStream {
        private final InputStream original;
        private long remaining;

        public LimitedInputStream(InputStream original, long limit) {
            this.original = original;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            remaining--;
            return original.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int bytesRead = original.read(b, off, toRead);
            if (bytesRead != -1) {
                remaining -= bytesRead;
            }
            return bytesRead;
        }

        @Override
        public void close() throws IOException {
            // Quan trọng: Đóng luồng gốc sau khi đọc xong
            original.close();
        }
    }
}