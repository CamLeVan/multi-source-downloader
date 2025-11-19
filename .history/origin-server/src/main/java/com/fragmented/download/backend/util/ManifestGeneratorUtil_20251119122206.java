package com.fragmented.download.backend.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import com.fragmented.download.core.model.ManifestModel;
import com.fragmented.download.core.model.PieceModel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ManifestGeneratorUtil {

    private static final long PIECE_SIZE = 1024 * 1024; // 1 MB

    public static void generateManifest(File sourceFile) throws IOException, NoSuchAlgorithmException {
        String manifestFileName = sourceFile.getName() + ".manifest.json";
        File manifestFile = new File(sourceFile.getParent(), manifestFileName);

        long fileSize = sourceFile.length();
        
        // Regenerate if manifest doesn't exist, or if file size changed (file was recreated)
        if (manifestFile.exists()) {
            // Check if file size matches - if not, regenerate manifest
            try {
                com.google.gson.Gson gson = new com.google.gson.Gson();
                com.fragmented.download.core.model.ManifestModel existingManifest = 
                    gson.fromJson(new java.io.FileReader(manifestFile), com.fragmented.download.core.model.ManifestModel.class);
                if (existingManifest.getFileSize() == fileSize && fileSize > 0) {
                    System.out.println("Manifest file already exists and matches file size: " + manifestFile.getAbsolutePath());
                    return;
                } else {
                    System.out.println("File size changed or invalid. Regenerating manifest...");
                }
            } catch (Exception e) {
                System.out.println("Error reading existing manifest. Regenerating...");
            }
        }

        System.out.println("Generating manifest for: " + sourceFile.getAbsolutePath() + " (size: " + fileSize + " bytes)");
        int numberOfPieces = (int) Math.ceil((double) fileSize / PIECE_SIZE);
        List<PieceModel> pieces = new ArrayList<>();

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

        try (InputStream is = new FileInputStream(sourceFile)) {
            byte[] buffer = new byte[8192];
            for (int i = 0; i < numberOfPieces; i++) {
                sha256.reset();
                long pieceBytesRead = 0;
                
                // Create a bounded input stream to read only one piece
                InputStream boundedIs = new BoundedInputStream(is, PIECE_SIZE);
                DigestInputStream dis = new DigestInputStream(boundedIs, sha256);

                while (dis.read(buffer) != -1) {
                    // Reading the stream updates the digest
                }

                byte[] hash = sha256.digest();
                String hexHash = bytesToHex(hash);

                // The origin and mirror sources are added here.
                List<String> sources = List.of(
                        "http://localhost:8080/files/" + sourceFile.getName(), // Origin
                        "http://mirror.vku.udn.vn/" + sourceFile.getName()      // Mirror
                );
                pieces.add(new PieceModel(i, hexHash, sources));

                System.out.println("Generated hash for piece " + i + ": " + hexHash);
            }
        }

        ManifestModel manifestModel = new ManifestModel(fileSize, PIECE_SIZE, pieces);

        try (FileWriter writer = new FileWriter(manifestFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(manifestModel, writer);
        }

        System.out.println("Successfully generated manifest file: " + manifestFile.getAbsolutePath());
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // Helper class to read only a certain number of bytes from an InputStream
    private static class BoundedInputStream extends InputStream {
        private final InputStream original;
        private long remaining;

        public BoundedInputStream(InputStream original, long limit) {
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
            // Don't close the original stream, as we're reading from it sequentially.
        }
    }
}
