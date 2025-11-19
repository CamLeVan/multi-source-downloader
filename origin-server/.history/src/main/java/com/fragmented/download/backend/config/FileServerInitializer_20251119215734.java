package com.fragmented.download.backend.config;

import com.fragmented.download.backend.util.ManifestGeneratorUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.security.NoSuchAlgorithmException;

@Component
public class FileServerInitializer implements CommandLineRunner {

    public static final String SERVER_FILE_DIR = "server_files";
    public static final String TEST_FILE_NAME = "2GB.zip";
    public static final long TEST_FILE_SIZE = 1024 * 1024 * 1024 * 2; // 2 GB

    @Override
    public void run(String... args) throws Exception {
        File directory = new File(SERVER_FILE_DIR);
        if (!directory.exists()) {
            directory.mkdir();
        }

        File file = new File(directory, TEST_FILE_NAME);
        if (!file.exists() || file.length() != TEST_FILE_SIZE) {
            System.out.println("Creating a dummy 2GB file for serving: " + file.getAbsolutePath());
            try {
                // Create the file (overwrite if exists) and write repeating pattern data in buffered chunks.
                final int BUFFER_SIZE = 4 * 1024 * 1024; // 4 MB
                byte[] buffer = new byte[BUFFER_SIZE];
                Arrays.fill(buffer, (byte) 0xA5); // recognizable pattern

                long remaining = TEST_FILE_SIZE;
                try (FileOutputStream fos = new FileOutputStream(file, false)) {
                    while (remaining > 0) {
                        int toWrite = (int) Math.min(buffer.length, remaining);
                        fos.write(buffer, 0, toWrite);
                        remaining -= toWrite;
                    }
                    fos.getFD().sync();
                }

                System.out.println("Successfully created and filled dummy file: " + file.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("Failed to create dummy file for server: " + e.getMessage());
            }
        }

        try {
    // Generate the manifest file for the test file
            ManifestGeneratorUtil.generateManifest(file);
            } catch (NoSuchAlgorithmException e) {
                System.err.println("FATAL: SHA-256 Algorithm not found. Server cannot start.");
                // Thoát ứng dụng Spring Boot
                System.exit(1); 
            } catch (IOException e) {
                System.err.println("FATAL: Failed to generate manifest. Server cannot start.");
                System.exit(1);
            }
    }
}
