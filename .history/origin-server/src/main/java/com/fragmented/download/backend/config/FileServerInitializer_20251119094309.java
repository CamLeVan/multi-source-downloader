package com.fragmented.download.backend.config;

import com.fragmented.download.backend.util.ManifestGeneratorUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.NoSuchAlgorithmException;

@Component
public class FileServerInitializer implements CommandLineRunner {

    public static final String SERVER_FILE_DIR = "server_files";
    public static final String TEST_FILE_NAME = "2GB.zip";
    public static final long TEST_FILE_SIZE = 1024 * 1024 * 1024 * 2L; // 2 GB

    @Override
    public void run(String... args) throws Exception {
        File directory = new File(SERVER_FILE_DIR);
        if (!directory.exists()) {
            directory.mkdir();
        }

        File file = new File(directory, TEST_FILE_NAME);
        if (!file.exists() || file.length() != TEST_FILE_SIZE) {
            System.out.println("Creating a dummy 20GB file for serving: " + file.getAbsolutePath());
            try {
                // Create the sparse file directly
                try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                    raf.setLength(TEST_FILE_SIZE);
                }

                // Fill the file with some dummy data to make it have a real size on disk
                try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                    // Write some data to make it non-empty
                    raf.seek(0);
                    raf.writeBytes("This is the start of the file.");
                    raf.seek(TEST_FILE_SIZE - 50);
                    raf.writeBytes("This is the end of the file.");
                    System.out.println("Filled dummy file with sample data.");
                }
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
