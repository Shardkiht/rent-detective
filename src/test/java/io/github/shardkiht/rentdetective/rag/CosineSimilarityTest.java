package io.github.shardkiht.rentdetective.rag;

import io.github.shardkiht.rentdetective.rag.store.CosineSimilarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CosineSimilarity 纯函数单测。
 */
class CosineSimilarityTest {

    private static final double DELTA = 1e-6;

    @Test
    void identicalVectors_shouldReturnOne() {
        float[] a = {1.0f, 2.0f, 3.0f};
        assertEquals(1.0, CosineSimilarity.compute(a, a), DELTA);
    }

    @Test
    void orthogonalVectors_shouldReturnZero() {
        float[] a = {1.0f, 0.0f};
        float[] b = {0.0f, 1.0f};
        assertEquals(0.0, CosineSimilarity.compute(a, b), DELTA);
    }

    @Test
    void oppositeVectors_shouldReturnNegativeOne() {
        float[] a = {1.0f, 0.0f};
        float[] b = {-1.0f, 0.0f};
        assertEquals(-1.0, CosineSimilarity.compute(a, b), DELTA);
    }

    @Test
    void knownValue_shouldMatchExpected() {
        // cos(0°) between (1,0) and (1,1) = 1/sqrt(2) ≈ 0.7071
        float[] a = {1.0f, 0.0f};
        float[] b = {1.0f, 1.0f};
        assertEquals(Math.sqrt(2) / 2, CosineSimilarity.compute(a, b), DELTA);
    }

    @Test
    void zeroVector_shouldReturnZero() {
        float[] a = {0.0f, 0.0f};
        float[] b = {1.0f, 2.0f};
        assertEquals(0.0, CosineSimilarity.compute(a, b), DELTA);
    }

    @Test
    void doubleOverload_shouldMatchFloatOverload() {
        float[] fa = {1.0f, 2.0f, 3.0f};
        float[] fb = {4.0f, 5.0f, 6.0f};
        double[] da = {1.0, 2.0, 3.0};
        double[] db = {4.0, 5.0, 6.0};

        assertEquals(CosineSimilarity.compute(fa, fb), CosineSimilarity.compute(da, db), 1e-5);
    }

    @Test
    void mismatchedLengths_shouldThrow() {
        float[] a = {1.0f, 2.0f};
        float[] b = {1.0f};
        assertThrows(IllegalArgumentException.class, () -> CosineSimilarity.compute(a, b));
    }

    @Test
    void nullInput_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> CosineSimilarity.compute(null, new float[]{1.0f}));
    }
}
