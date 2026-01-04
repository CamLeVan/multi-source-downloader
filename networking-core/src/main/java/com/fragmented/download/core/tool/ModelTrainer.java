package com.fragmented.download.core.tool;

import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Random;

/**
 * Utility to train the AI model for SmartDetector.
 * It reads the CSV, balances the dataset (AUGMENTATION), trains J48 tree, and
 * saves the .model file.
 */
public class ModelTrainer {

    private static final String CSV_FILE = "network_data.csv"; // Path relative to project root
    private static final String MODEL_FILE = "network_anomaly_model.model";

    public static void main(String[] args) {
        System.out.println("=== AI Model Trainer for Multi-Source Downloader ===");
        try {
            // 1. Setup Weaka Attributes (Schema)
            ArrayList<Attribute> attributes = new ArrayList<>();
            attributes.add(new Attribute("responseTimeMs"));
            attributes.add(new Attribute("speedKBps"));

            ArrayList<String> classValues = new ArrayList<>();
            classValues.add("NORMAL");
            classValues.add("ANOMALY");
            attributes.add(new Attribute("label", classValues));

            // 2. Load Real Data from CSV
            System.out.println("Reading data from: " + CSV_FILE);
            ArrayList<double[]> rawData = readCsv(CSV_FILE);
            System.out.println("Loaded " + rawData.size() + " raw records.");

            // 3. Create Balanced Dataset (Augmentation)
            Instances trainingData = new Instances("NetworkData", attributes, 0);
            trainingData.setClassIndex(2); // Label is last attribute

            int normalCount = 0;
            int anomalyCount = 0;

            // Add real data (Max 5000 records to keep training fast)
            int limit = 5000;
            for (double[] row : rawData) {
                if (limit-- <= 0)
                    break;

                // [latency, speed, isAnomaly (0.0 or 1.0)]
                trainingData.add(new DenseInstance(1.0, row));
                if (row[2] == 0.0)
                    normalCount++;
                else
                    anomalyCount++;
            }

            System.out.println("Real Data Stats: NORMAL=" + normalCount + ", ANOMALY=" + anomalyCount);

            // 4. Augment Anomalies (Generate fake bad connections)
            // We want roughly 50/50 balance.
            int neededAnomalies = normalCount - anomalyCount;
            if (neededAnomalies > 0) {
                System.out.println("⚡ Augmenting dataset with " + neededAnomalies + " synthetic ANOMALY records...");
                Random rand = new Random();
                for (int i = 0; i < neededAnomalies; i++) {
                    double latency, speed;

                    // Logic to generate BAD data (High latency OR Low Speed)
                    if (rand.nextBoolean()) {
                        // High Latency: 2001ms - 10000ms
                        latency = 2001 + rand.nextInt(8000);
                        speed = 50 + rand.nextDouble() * 500; // Normal speed
                    } else {
                        // Low Speed: 0 - 49 KB/s
                        latency = 50 + rand.nextInt(1000); // Normal latency
                        speed = rand.nextDouble() * 49.0;
                    }

                    double[] badRow = new double[] { latency, speed, 1.0 }; // 1.0 = ANOMALY
                    trainingData.add(new DenseInstance(1.0, badRow));
                }
            }

            System.out.println("Training Dataset Size: " + trainingData.numInstances());

            // 5. Train Model (J48 Decision Tree)
            System.out.println("Training J48 Decision Tree...");
            J48 tree = new J48();
            tree.buildClassifier(trainingData);

            System.out.println("\n=== Model Structure ===");
            System.out.println(tree.toString());

            // 5.1. Evaluate Model
            System.out.println("\n=== Evaluation (Self-Test on Training Data) ===");
            weka.classifiers.Evaluation eval = new weka.classifiers.Evaluation(trainingData);
            eval.evaluateModel(tree, trainingData);
            
            System.out.println(eval.toSummaryString());
            System.out.println(eval.toClassDetailsString());
            System.out.println(eval.toMatrixString());

            // 6. Save Model
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(MODEL_FILE))) {
                oos.writeObject(tree);
            }

            System.out.println("✅ SUCCESS: Model saved to " + new File(MODEL_FILE).getAbsolutePath());
            System.out.println("👉 Copy this file to your root directory if it's not already there.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ArrayList<double[]> readCsv(String filePath) {
        ArrayList<double[]> list = new ArrayList<>();
        File f = new File(filePath);
        if (!f.exists()) return list;

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            br.readLine(); // Skip Header
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 4) continue;


                       
                try {
                    double latency = Double.parseDouble(parts[0]);
                    double speed = Double.parseDouble(parts[1]);
                    // parts[2] is isSuccess (boolean), let's ignore or use to verify
                    String label = parts[3].trim();
                     
                    // Map label to index: NORMAL=0, ANOMALY=1
                    double classIdx = label.equals("ANOMALY") ? 1.0 : 0.0;
                    
                    list.add(new double[]{latency, speed, classIdx});
                } catch (NumberFormatException e) {
                    // ignore malformed lines
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
