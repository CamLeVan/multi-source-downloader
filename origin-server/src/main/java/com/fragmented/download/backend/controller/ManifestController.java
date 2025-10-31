package com.fragmented.download.backend.controller;

import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.core.model.PieceModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RestController
public class ManifestController {

    @GetMapping("/manifest")
    public ManifestModel getManifest() {
        long fileSize = 1024 * 1024 * 1024; // 1 GB
        long pieceSize = 1024 * 1024; // 1 MB
        int numberOfPieces = (int) Math.ceil((double) fileSize / pieceSize);

        List<PieceModel> pieces = IntStream.range(0, numberOfPieces)
                .mapToObj(i -> new PieceModel(
                        i,
                        "sha256-hash-of-piece-" + i, // Placeholder hash
                        List.of(
                                "http://localhost:8080/file/piece/" + i,
                                "http://localhost:8081/file/piece/" + i
                        )
                ))
                .collect(Collectors.toList());

        return new ManifestModel(fileSize, pieceSize, pieces);
    }
}
