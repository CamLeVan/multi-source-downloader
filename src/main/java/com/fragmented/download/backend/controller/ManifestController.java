package com.fragmented.download.backend.controller;

import com.fragmented.download.core.model.ManifestModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;

@RestController
public class ManifestController {

    private final ManifestModel manifest;
    private final ResourceLoader resourceLoader;

    @Autowired
    public ManifestController(ManifestModel manifest, ResourceLoader resourceLoader) {
        this.manifest = manifest;
        this.resourceLoader = resourceLoader;
    }

    @GetMapping("/manifest/{fileId}")
    public ManifestModel getManifest(@PathVariable String fileId) {
        // Return the pre-built manifest object, no recalculation needed
        return this.manifest;
    }

    @GetMapping("/file/{fileId}")
    public ResponseEntity<ResourceRegion> getFile(
            @PathVariable String fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) throws IOException {

        // For simplicity, we'll use the fileId to load a file from the static resources.
        // In a real application, you'd have a service to map fileId to a file path.
        Resource resource = resourceLoader.getResource("classpath:static/" + fileId);

        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        long contentLength = resource.contentLength();

        if (rangeHeader == null) {
            ResourceRegion region = new ResourceRegion(resource, 0, contentLength);
            return ResponseEntity.status(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(contentLength)
                    .body(region);
        }

        try {
            HttpRange range = HttpRange.parseRanges(rangeHeader).get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = end - start + 1;

            ResourceRegion region = new ResourceRegion(resource, start, rangeLength);

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + contentLength)
                    .contentLength(rangeLength)
                    .body(region);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}