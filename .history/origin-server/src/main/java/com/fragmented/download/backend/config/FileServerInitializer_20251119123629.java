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
    public static final long TEST_FILE_SIZE = 1024L * 1024 * 1024 * 2; // 2 GB

    @Override
    public void run(String... args) throws Exception {
        File directory = new File(SERVER_FILE_DIR);
        if (!directory.exists()) {
            directory.mkdir();
        }

        File file = new File(directory, TEST_FILE_NAME);
        File manifestFile = new File(directory, TEST_FILE_NAME + ".manifest.json");
        
        // If file doesn't exist or has wrong size, recreate it and delete old manifest
        if (!file.exists() || file.length() != TEST_FILE_SIZE) {
            // Delete old manifest if file size is wrong
            if (manifestFile.exists() && file.exists() && file.length() != TEST_FILE_SIZE) {
                System.out.println("File size mismatch. Deleting old manifest: " + manifestFile.getAbsolutePath());
                manifestFile.delete();
            }
            
            System.out.println("Creating a dummy 2GB file for serving: " + file.getAbsolutePath());
            try {
                // Delete old file if exists
                if (file.exists()) {
                    file.delete();
                }
                
                // Create sparse file - write only to start and end positions
                // This creates a sparse file that doesn't actually use 2GB on disk
                System.out.println("Creating sparse file (writing only start and end markers)...");
                boolean fileCreated = false;
                
                try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                    // Method 1: Try setLength first (works on Linux/macOS and some Windows configurations)
                    try {
                        raf.setLength(TEST_FILE_SIZE);
                        System.out.println("File length set successfully using setLength()");
                        fileCreated = true;
                    } catch (IOException e) {
                        // Method 2: On Windows, setLength might fail for very large files
                        // Use seek + write to end to create sparse file
                        System.out.println("setLength() failed, using seek method: " + e.getMessage());
                        
                        // Write to start of file
                        raf.seek(0);
                        raf.writeBytes("START: 2GB test file. Created: " + new java.util.Date());
                        
                        // Seek to near end and write - this extends the file
                        // Write to position just before end to create sparse file
                        long endPos = TEST_FILE_SIZE - 100;
                        if (endPos > 0) {
                            raf.seek(endPos);
                            raf.writeBytes("END: 2GB test file. Total size: " + TEST_FILE_SIZE + " bytes.");
                        }
                        
                        // Force file system to recognize the full size
                        // On Windows, we need to explicitly set the length after writing
                        try {
                            raf.setLength(TEST_FILE_SIZE);
                            System.out.println("File length set after seek+write");
                            fileCreated = true;
                        } catch (IOException e2) {
                            // If still fails, try using FileChannel
                            System.out.println("setLength() still failed, trying FileChannel method...");
                            try (java.nio.channels.FileChannel channel = raf.getChannel()) {
                                channel.truncate(TEST_FILE_SIZE);
                                System.out.println("File length set using FileChannel.truncate()");
                                fileCreated = true;
                            } catch (Exception e3) {
                                System.err.println("All methods failed. File may not be correct size.");
                                System.err.println("Error: " + e3.getMessage());
                                e3.printStackTrace();
                            }
                        }
                    }
                    
                    // If setLength succeeded, write markers
                    if (fileCreated) {
                        raf.seek(0);
                        raf.writeBytes("START: 2GB test file. Created: " + new java.util.Date());
                        raf.seek(TEST_FILE_SIZE - 100);
                        raf.writeBytes("END: 2GB test file. Total size: " + TEST_FILE_SIZE + " bytes.");
                    }
                }
                
                // Verify file size
                long actualSize = file.length();
                if (actualSize != TEST_FILE_SIZE) {
                    System.err.println("WARNING: File size is " + actualSize + " bytes, expected " + TEST_FILE_SIZE + " bytes");
                    System.err.println("Difference: " + (TEST_FILE_SIZE - actualSize) + " bytes");
                } else {
                    System.out.println("File created successfully: " + actualSize + " bytes (" + (actualSize / (1024L * 1024)) + " MB)");
                }
            } catch (IOException e) {
                System.err.println("Failed to create dummy file for server: " + e.getMessage());
                e.printStackTrace();
                // Don't exit - let server start with empty file, user can fix manually
            }
        }

        // Verify file exists and has correct size before generating manifest
        long finalFileSize = file.length();
        if (finalFileSize != TEST_FILE_SIZE) {
            System.err.println("ERROR: File size is incorrect: " + finalFileSize + " bytes, expected " + TEST_FILE_SIZE + " bytes");
            System.err.println("Cannot generate manifest with incorrect file size.");
            System.err.println("Please check file: " + file.getAbsolutePath());
            System.err.println("Server will start but file download may not work correctly.");
            // Don't exit - let server start, user can fix manually
        } else {
            try {
                // Generate the manifest file for the test file
                // This will regenerate if file size changed
                System.out.println("File size verified: " + finalFileSize + " bytes. Generating manifest...");
                ManifestGeneratorUtil.generateManifest(file);
                System.out.println("Manifest generation completed successfully.");
            } catch (NoSuchAlgorithmException e) {
                System.err.println("FATAL: SHA-256 Algorithm not found. Server cannot start.");
                e.printStackTrace();
                System.exit(1); 
            } catch (IOException e) {
                System.err.println("FATAL: Failed to generate manifest. Server cannot start.");
                e.printStackTrace();
                System.exit(1);
            }
        }
    }
}
