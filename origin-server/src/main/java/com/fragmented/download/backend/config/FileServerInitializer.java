package com.fragmented.download.backend.config;

import com.fragmented.download.backend.util.ManifestGeneratorUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

@Component
public class FileServerInitializer implements CommandLineRunner {

    public static final String SERVER_FILE_DIR = "server_files";
    public static final String TEST_FILE_NAME = "2GB.zip";
    public static final long TEST_FILE_SIZE = 1024 * 1024 * 1024 * 2; // 2 GB

    @Override
    public void run(String... args) throws Exception {
        File directory = new File(SERVER_FILE_DIR);
        if (!directory.exists()) {
            boolean created = directory.mkdir();
            if (created) {
                System.out.println("Created server directory: " + directory.getAbsolutePath());
            }
        }

        // 1. Create dummy file only if directory is empty (optional convenience)
        File dummyFile = new File(directory, TEST_FILE_NAME);
        if (!dummyFile.exists() && directory.listFiles() != null && directory.listFiles().length == 0) {
            createDummyFile(dummyFile);
        }

        // 2. Scan directory and generate manifests for ALL files
        System.out.println("Scanning for files in: " + directory.getAbsolutePath());
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                // Skip directories and existing manifest files
                if (file.isDirectory() || file.getName().endsWith(".manifest.json")) {
                    continue;
                }

                // Generate manifest for this file
                try {
                    System.out.println("Checking manifest for: " + file.getName());
                    ManifestGeneratorUtil.generateManifest(file);
                } catch (Exception e) {
                    System.err.println("Failed to generate manifest for " + file.getName() + ": " + e.getMessage());
                }
            }
        }

        System.out.println("Server initialization complete. Ready to serve files!");
    }

    private void createDummyFile(File file) {
        System.out.println("Creating a dummy 2GB file for testing...");
        try {
            final int BUFFER_SIZE = 4 * 1024 * 1024; // 4 MB
            byte[] buffer = new byte[BUFFER_SIZE];
            Arrays.fill(buffer, (byte) 0xA5);

            // Create only 100MB dummy to be faster
            long size = 100L * 1024 * 1024;

            try (FileOutputStream fos = new FileOutputStream(file, false)) {
                long remaining = size;
                while (remaining > 0) {
                    int toWrite = (int) Math.min(buffer.length, remaining);
                    fos.write(buffer, 0, toWrite);
                    remaining -= toWrite;
                }
                fos.getFD().sync();
            }
            System.out.println("Created dummy file: " + file.getName());
        } catch (IOException e) {
            System.err.println("Error creating dummy file: " + e.getMessage());
        }
    }
}
