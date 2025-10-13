package com.fragmented.download.backend.config;

import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.core.model.PieceModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Configuration
public class ManifestConfig {

    @Bean
    public ManifestModel defaultManifest() {
        long fileSize = 20971520000L; // 20 GB
        long pieceSize = 1048576L;    // 1 MB
        int numberOfPieces = 20000;

        List<PieceModel> pieces = IntStream.range(0, numberOfPieces)
                .mapToObj(i -> new PieceModel(
                        i,
                        "HASH_" + i,
                        Arrays.asList("origin", "mirror")
                ))
                .collect(Collectors.toList());

        return new ManifestModel(fileSize, pieceSize, pieces);
    }
}
