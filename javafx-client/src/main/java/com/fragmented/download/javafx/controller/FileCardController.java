package com.fragmented.download.javafx.controller;

import com.fragmented.download.javafx.model.FileInfo;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * Phase 1.7: Controller cho File Card component
 * Hiển thị file info dạng card với metadata đầy đủ
 */
public class FileCardController {
    
    @FXML
    private HBox root; // Root node từ FXML
    
    @FXML
    private Label iconLabel;
    
    @FXML
    private Label fileNameLabel;
    
    @FXML
    private Label categoryLabel;
    
    @FXML
    private Label descriptionLabel;
    
    @FXML
    private Label sizeLabel;
    
    @FXML
    private Label dateLabel;
    

    @FXML
    private Label manifestStatusLabel;
    
    @FXML
    private Label downloadsLabel;
    
    @FXML
    private Label ratingLabel;
    
    @FXML
    private Label popularTagLabelTop;
    
    @FXML
    private Label mirrorsVersionLabel;
    
    @FXML
    private Button downloadButton;
    
    private FileInfo fileInfo;
    private Runnable onDownloadCallback; // Callback khi click Download button
    
    /**
     * Set file info và bind UI
     */
    public void setFileInfo(FileInfo fileInfo, Runnable onDownloadCallback) {
        this.fileInfo = fileInfo;
        this.onDownloadCallback = onDownloadCallback;
        
        if (fileInfo != null) {
            // Bind data
            fileNameLabel.setText(fileInfo.getFileName());
            sizeLabel.setText(fileInfo.getFormattedSize());
            dateLabel.setText(fileInfo.getFormattedDate());
            
            // Mirrors và Version (như trong ảnh: "3 mirrors • v22.04.3")
            int mirrors = fileInfo.getMirrorsCount();
            String mirrorsText = mirrors + " mirrors"; // TODO: Add version info "• v22.04.3"
            if (mirrorsVersionLabel != null) {
                mirrorsVersionLabel.setText(mirrorsText);
            }
            
            // Downloads (placeholder - sẽ thêm backend tracking sau)
            if (downloadsLabel != null) {
                downloadsLabel.setText("0 downloads"); // TODO: Add downloads count from backend
            }
            
            // Rating (placeholder - sẽ thêm backend tracking sau)
            if (ratingLabel != null) {
                ratingLabel.setText("N/A"); // TODO: Add rating from backend
            }
            
            // Popular tag (hiển thị nếu file lớn hoặc có nhiều mirrors)
            boolean isPopular = fileInfo.getSize() > 50 * 1024 * 1024 || mirrors > 2; // > 50MB or > 2 mirrors
            if (popularTagLabelTop != null) {
                popularTagLabelTop.setVisible(isPopular);
                popularTagLabelTop.setText("Popular");
            }
            
            // Description (dynamic based on file type)
            String description = generateDescription(fileInfo.getFileName(), fileInfo.getSize());
            if (descriptionLabel != null) {
                descriptionLabel.setText(description);
            }
            
            // Category và Icon
            String category = detectCategory(fileInfo.getFileName());
            categoryLabel.setText(category);
            
            // Set icon dựa trên file type
            String icon = getIconForFile(fileInfo.getFileName());
            iconLabel.setText(icon);
        }
    }
    
    /**
     * Phase 3: Get icon emoji cho file type
     */
    private String getIconForFile(String fileName) {
        if (fileName == null) return "📄";
        
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") || 
            lower.endsWith(".tar") || lower.endsWith(".gz")) {
            return "📦"; // Archive
        } else if (lower.endsWith(".exe") || lower.endsWith(".msi") || lower.endsWith(".app")) {
            return "💻"; // Application
        } else if (lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return "📄"; // Document
        } else if (lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv") ||
                   lower.endsWith(".mov") || lower.endsWith(".wmv")) {
            return "🎬"; // Video
        } else if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac")) {
            return "🎵"; // Audio
        } else if (lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".gif") ||
                   lower.endsWith(".jpeg") || lower.endsWith(".bmp")) {
            return "🖼️"; // Image
        } else if (lower.endsWith(".java") || lower.endsWith(".js") || lower.endsWith(".py") ||
                   lower.endsWith(".cpp") || lower.endsWith(".c")) {
            return "💻"; // Code
        } else if (lower.endsWith(".txt") || lower.endsWith(".log")) {
            return "📝"; // Text
        }
        return "📄"; // Default
    }
    
    /**
     * Detect category từ file extension
     */
    private String detectCategory(String fileName) {
        if (fileName == null) return "File";
        
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") ||
            lower.endsWith(".tar") || lower.endsWith(".gz")) {
            return "Archive";
        } else if (lower.endsWith(".exe") || lower.endsWith(".msi") || lower.endsWith(".app")) {
            return "Application";
        } else if (lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return "Document";
        } else if (lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv") ||
                   lower.endsWith(".mov") || lower.endsWith(".wmv")) {
            return "Video";
        } else if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac")) {
            return "Audio";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".gif") ||
                   lower.endsWith(".jpeg") || lower.endsWith(".bmp")) {
            return "Image";
        } else if (lower.endsWith(".java") || lower.endsWith(".js") || lower.endsWith(".py") ||
                   lower.endsWith(".cpp") || lower.endsWith(".c")) {
            return "Code";
        } else if (lower.endsWith(".txt") || lower.endsWith(".log")) {
            return "Text";
        }
        return "File";
    }
    
    /**
     * Handler cho Download button click
     */
    @FXML
    private void onDownloadClick() {
        if (onDownloadCallback != null && fileInfo != null) {
            onDownloadCallback.run();
        }
    }
    
    /**
     * Generate description based on file type and size
     */
    private String generateDescription(String fileName, long size) {
        String lower = fileName.toLowerCase();
        String sizeText = formatSize(size);
        
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")) {
            return "Archive file ready for download. Size: " + sizeText;
        } else if (lower.endsWith(".exe") || lower.endsWith(".msi")) {
            return "Application installer. Size: " + sizeText;
        } else if (lower.endsWith(".pdf")) {
            return "Document file. Size: " + sizeText;
        } else if (lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv")) {
            return "Video file. Size: " + sizeText;
        } else if (lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".gif")) {
            return "Image file. Size: " + sizeText;
        }
        return "File available for download. Size: " + sizeText;
    }
    
    /**
     * Format size for description
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * Get root node (HBox)
     */
    public HBox getRoot() {
        return root;
    }
}

