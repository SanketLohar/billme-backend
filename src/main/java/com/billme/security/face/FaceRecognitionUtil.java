package com.billme.security.face;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public class FaceRecognitionUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Convert JSON string to double[]
    public static double[] parseEmbedding(String embeddingJson) {
        try {
            List<Double> list = objectMapper.readValue(
                    embeddingJson,
                    new TypeReference<List<Double>>() {}
            );

            double[] arr = new double[list.size()];
            for (int i = 0; i < list.size(); i++) {
                arr[i] = list.get(i);
            }

            return arr;

        } catch (Exception e) {
            throw new RuntimeException("Invalid embedding format");
        }
    }

    // Cosine similarity calculation
    public static double cosineSimilarity(double[] a, double[] b) {

        if (a.length != b.length) {
            throw new RuntimeException("Embedding dimensions mismatch");
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += Math.pow(a[i], 2);
            normB += Math.pow(b[i], 2);
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static boolean isMatch(String storedEmbedding,
                                  String paymentEmbedding,
                                  double threshold) {

        double[] stored = parseEmbedding(storedEmbedding);
        double[] payment = parseEmbedding(paymentEmbedding);

        double similarity = cosineSimilarity(stored, payment);

        return similarity >= threshold;
    }
}
