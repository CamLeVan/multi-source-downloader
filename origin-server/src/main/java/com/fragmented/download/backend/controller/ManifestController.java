package com.fragmented.download.backend.controller;

import com.fragmented.download.backend.config.FileServerInitializer;
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
     * Phục vụ file Manifest (JSON) cho client.
     * Task Tuần 1.
     */
    @GetMapping("/manifest/{fileName}")
    public ManifestModel getManifest(@PathVariable String fileName) throws FileNotFoundException {
        String manifestFileName = fileName + ".manifest.json";
        Path manifestPath = Paths.get(FileServerInitializer.SERVER_FILE_DIR, manifestFileName);

        if (!Files.exists(manifestPath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Manifest file not found");
        }

        try (FileReader reader = new FileReader(manifestPath.toFile())) {
            return gson.fromJson(reader, ManifestModel.class);
        } catch (IOException e) {
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
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader)
            throws IOException {

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