package io.github.shardkiht.rentdetective.rag.store;

/**
 * 余弦相似度纯函数工具类。
 * <p>
 * 设计取舍：百级数据量（本项目 104 条房源）应用层暴力扫描足够，
 * 不引入向量数据库（Milvus / pgvector），面试可逐条讲原理：
 * cos(θ) = dot(a,b) / (‖a‖·‖b‖)，值域 [-1,1]，归一化后等价于内积。
 * <p>
 * 实际计算委托给 {@link VectorUtils#cosineSimilarity(float[], float[])}，
 * 该类作为 RAG 检索的语义入口，方便上层按名字理解用途。
 */
public final class CosineSimilarity {

    private CosineSimilarity() {
    }

    /**
     * 计算两个 float 向量的余弦相似度。
     *
     * @return [0.0, 1.0] 范围内的相似度（零向量返回 0.0）
     */
    public static double compute(float[] a, float[] b) {
        return VectorUtils.cosineSimilarity(a, b);
    }

    /**
     * 计算两个 double 向量的余弦相似度。
     * 内部转为 float[] 委托给 {@link VectorUtils#cosineSimilarity(float[], float[])}。
     */
    public static double compute(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) {
            throw new IllegalArgumentException("Vectors must be non-null and of the same length");
        }
        float[] fa = new float[a.length];
        float[] fb = new float[b.length];
        for (int i = 0; i < a.length; i++) {
            fa[i] = (float) a[i];
            fb[i] = (float) b[i];
        }
        return VectorUtils.cosineSimilarity(fa, fb);
    }
}
