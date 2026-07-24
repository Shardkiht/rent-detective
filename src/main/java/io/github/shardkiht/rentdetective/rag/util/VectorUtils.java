package io.github.shardkiht.rentdetective.rag.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class VectorUtils {

    private VectorUtils() {
    }

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            throw new IllegalArgumentException("Vectors must be non-null and of the same length");
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static byte[] floatToBytes(float[] vector) {
        if (vector == null) {
            return new byte[0];
        }
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (float f : vector) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    public static float[] bytesToFloat(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new float[0];
        }
        int length = bytes.length / 4;
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[length];
        for (int i = 0; i < length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }
}
