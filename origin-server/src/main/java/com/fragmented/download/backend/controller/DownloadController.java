package com.fragmented.download.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/download")
public class DownloadController {

    @Value("${file-server.directory}")
    private String fileDirectory;

    @GetMapping("/{fileName}")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable String fileName,
            @RequestHeader(value = "Range", required = false) String rangeHeader) throws IOException {

        Path filePath = Paths.get(fileDirectory, fileName);
        File file = filePath.toFile();
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        long fileLength = file.length();

        if (rangeHeader == null) {
            // If no range is requested, return a 416 Range Not Satisfiable to indicate that range is required
            // Or optionally, return the full file. For this use case, we expect clients to use range requests.
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
        }

        String[] ranges = rangeHeader.substring("bytes=".length()).split("-");
        long startByte = Long.parseLong(ranges[0]);
        long endByte;
        if (ranges.length > 1 && !ranges[1].trim().isEmpty()) {
            endByte = Long.parseLong(ranges[1]);
        } else {
            endByte = fileLength - 1;
        }

        // Ensure range is valid
        if (startByte < 0 || startByte >= fileLength || endByte < startByte || endByte >= fileLength) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLength)
                    .build();
        }

        long contentLength = (endByte - startByte) + 1;
        byte[] content = new byte[(int) contentLength];

        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            randomAccessFile.seek(startByte);
            randomAccessFile.readFully(content);
        }

        String contentRange = "bytes " + startByte + "-" + endByte + "/" + fileLength;

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                .header(HttpHeaders.CONTENT_RANGE, contentRange)
                .body(content);
    }
}
