
package com.fragmented.download.core.logic;

import com.fragmented.download.core.model.NetworkMetrics;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.File;
import java.util.ArrayList;

/**
 * SmartDetector: A hybrid anomaly detector for network connections.
 * It uses a trained Machine Learning model (if available) or Fallback Rules.
 * 
 * UPDATE: Integrated Weka for AI-based prediction.
 */
public class SmartDetector {

    // Thresholds (Fallback Rules)
    private static final long MAX_LATENCY_MS = 30000; // 30 seconds
    private static final double MIN_SPEED_KBPS = 10.0; // 10 KB/s

    // AI Model
    private static final String MODEL_FILE = "network_anomaly_model.model";
    private Classifier classifier;
    private Instances dataStructure;
    private boolean isModelLoaded = false;

    public SmartDetector() {
        // Logging initialization is handled by AsyncNetworkLogger singleton
        loadModel();
    }

    private void loadModel() {
        File modelFile = new File(MODEL_FILE);
        if (modelFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelFile))) {
                classifier = (Classifier) ois.readObject();

                // Define dataset structure (Must match training data)
                ArrayList<Attribute> attributes = new ArrayList<>();
                attributes.add(new Attribute("responseTimeMs"));
                attributes.add(new Attribute("speedKBps"));

                // Class attribute: {NORMAL, ANOMALY}
                ArrayList<String> classValues = new ArrayList<>();
                classValues.add("NORMAL");
                classValues.add("ANOMALY");
                attributes.add(new Attribute("label", classValues));

                dataStructure = new Instances("NetworkData", attributes, 0);
                dataStructure.setClassIndex(2); // Label is the last attribute

                isModelLoaded = true;
                System.out.println("[SmartDetector] AI Model loaded successfully from " + MODEL_FILE);
            } catch (Exception e) {
                System.err.println("[SmartDetector] Failed to load AI model: " + e.getMessage());
                isModelLoaded = false;
            }
        } else {
            System.out.println("[SmartDetector] No AI model found. Using Rule-based logic.");
        }
    }

    /**
     * Evaluates if the current network metrics indicate an anomaly (poor
     * connection).
     * 
     * @param metrics The collected network metrics.
     * @return true if an anomaly is detected, false otherwise.
     */
    public boolean isAnomaly(NetworkMetrics metrics) {
        // 1. Always fail fast on connection errors
        if (!metrics.isSuccess()) {
            // Log & Return logic is below
            logAndReturn(metrics, true, "CONNECTION_FAILED");
            return true;
        }

        boolean isAnomaly;
        String decisionSource;

        if (isModelLoaded) {
            // 2. AI Prediction Mode
            try {
                Instance instance = new DenseInstance(3);
                instance.setDataset(dataStructure);
                instance.setValue(0, metrics.getResponseTimeMs());
                instance.setValue(1, metrics.getDownloadSpeedKBps());
                // Label is missing, to be predicted

                double prediction = classifier.classifyInstance(instance);
                String predictedLabel = dataStructure.classAttribute().value((int) prediction);

                isAnomaly = "ANOMALY".equals(predictedLabel);

                // Cập nhật: Nếu AI báo lỗi, nhưng thông số mạng thực tế vẫn NẰM TRONG GIỚI HẠN
                // AN TOÀN của checkRules,
                // Thì chúng ta sẽ DUNG TÚNG (Bỏ qua dự đoán của AI) để tránh sập luồng tải oan
                // uổng.
                if (isAnomaly && !checkRules(metrics)) {
                    isAnomaly = false;
                    decisionSource = "AI_TOLERANCE_MODE";
                } else {
                    decisionSource = "AI_MODEL";
                }
            } catch (Exception e) {
                System.err.println("[SmartDetector] AI Prediction Error: " + e.getMessage());
                isAnomaly = checkRules(metrics); // Fallback to rules on error
                decisionSource = "RULE_FALLBACK";
            }
        } else {
            // 3. Rule-based Mode
            isAnomaly = checkRules(metrics);
            decisionSource = "RULE_BASED";
        }

        // Console Alert for Anomaly
        if (isAnomaly) {
            System.out.println("⚠ Anomaly Detected (" + decisionSource + "): "
                    + "Ping=" + metrics.getResponseTimeMs() + "ms, Speed=" + metrics.getDownloadSpeedKBps() + "KB/s");
        }

        // Log to CSV asynchronously for training/retraining
        AsyncNetworkLogger.getInstance().log(metrics, isAnomaly ? "ANOMALY" : "NORMAL");

        return isAnomaly;
    }

    private boolean checkRules(NetworkMetrics metrics) {
        if (metrics.getResponseTimeMs() > MAX_LATENCY_MS)
            return true;
        if (metrics.getDownloadSpeedKBps() < MIN_SPEED_KBPS)
            return true;
        return false;
    }

    private void logAndReturn(NetworkMetrics metrics, boolean isAnomaly, String reason) {
        AsyncNetworkLogger.getInstance().log(metrics, isAnomaly ? "ANOMALY" : "NORMAL");
    }
}