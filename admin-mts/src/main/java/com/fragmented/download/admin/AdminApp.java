package com.fragmented.download.admin;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main entry point for Admin Client Application
 */
public class AdminApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load main dashboard FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AdminDashboard.fxml"));
        Parent root = loader.load();

        // Load CSS
        Scene scene = new Scene(root, 1200, 800);
        String cssPath = getClass().getResource("/styles/admin.css").toExternalForm();
        scene.getStylesheets().add(cssPath);

        primaryStage.setTitle("Multi-Source Downloader - Admin Panel");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

