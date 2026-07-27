package io.github.shardkiht.rentdetective.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shardkiht.rentdetective.app.entity.Listing;
import io.github.shardkiht.rentdetective.app.mapper.ListingMapper;
import io.github.shardkiht.rentdetective.llm.api.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RAG 核心服务：向量化入库 + Top-K 相似检索。
 * <p>
 * 设计取舍：全表加载到内存计算余弦（104 条数据量完全可行），
 * 向量以 JSON 字符串存储在 MySQL 列中，不引入专用向量数据库。
 * Agent 循环手写，工具可插拔；识坑规则来自人工标注。
 */
@Service
public class CaseVectorService {

    private static final Logger log = LoggerFactory.getLogger(CaseVectorService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<float[]> FLOAT_ARRAY_TYPE = new TypeReference<>() {};

    private final EmbeddingClient embeddingClient;
    private final CaseVectorMapper caseVectorMapper;
    private final ListingMapper listingMapper;

    public CaseVectorService(EmbeddingClient embeddingClient,
                             CaseVectorMapper caseVectorMapper,
                             ListingMapper listingMapper) {
        this.embeddingClient = embeddingClient;
        this.caseVectorMapper = caseVectorMapper;
        this.listingMapper = listingMapper;
    }

    /**
     * 向量化入库：读 listings 全部行，title+"\n"+description 拼接 → embed → 写 case_vectors。
     * 已存在（按 listing_id）则跳过。
     * 批量调用之间 sleep 200ms，避免打爆本地 Ollama。
     */
    public void embedAll() {
        List<Listing> listings = listingMapper.selectList(null);
        Set<Integer> existingIds = caseVectorMapper.selectList(null)
                .stream()
                .map(CaseVector::getListingId)
                .collect(Collectors.toSet());

        int embedded = 0;
        for (Listing listing : listings) {
            int listingId = listing.getId().intValue();
            if (existingIds.contains(listingId)) {
                continue;
            }

            String text = buildEmbedText(listing);
            try {
                float[] vector = embeddingClient.embed(text);
                String vectorJson = serializeVector(vector);

                CaseVector cv = new CaseVector();
                cv.setListingId(listingId);
                cv.setEmbeddedText(text);
                cv.setVectorJson(vectorJson);
                caseVectorMapper.insert(cv);
                embedded++;

                // 避免打爆本地 Ollama
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("embedAll 被中断，已处理 {} 条", embedded);
                return;
            } catch (Exception e) {
                log.error("embedAll 处理 listing_id={} 失败: {}", listingId, e.getMessage());
            }
        }
        log.info("embedAll 完成，新嵌入 {} 条（总计 {} 条房源）", embedded, listings.size());
    }

    /**
     * 强制重建：先清空 case_vectors 再 embedAll。
     */
    public void reembedAll() {
        caseVectorMapper.delete(null);
        log.info("已清空 case_vectors 表，开始重新嵌入...");
        embedAll();
    }

    /**
     * Top-K 检索：输入文本 → 向量化 → 全表余弦 → 排序取 Top-K → 关联 listings 获取元数据。
     *
     * @param text 查询文本
     * @param k    返回数量
     * @return 按相似度降序的相似案例列表
     */
    public List<SimilarCase> search(String text, int k) {
        return search(text, k, null);
    }

    /**
     * Top-K 检索（带查询方 phone 比对）。
     *
     * @param text       查询文本
     * @param k          返回数量
     * @param queryPhone 查询方联系电话，可为 null
     * @return 按相似度降序的相似案例列表
     */
    public List<SimilarCase> search(String text, int k, String queryPhone) {
        float[] queryVector = embeddingClient.embed(text);

        List<CaseVector> allVectors = caseVectorMapper.selectList(null);
        if (allVectors.isEmpty()) {
            return Collections.emptyList();
        }

        // 收集所有 listingId，批量查询关联的 Listing
        List<Integer> listingIds = allVectors.stream()
                .map(CaseVector::getListingId)
                .toList();
        Map<Integer, Listing> listingMap = loadListingMap(listingIds);

        // 逐条计算余弦相似度
        List<ScoredVector> scored = new ArrayList<>();
        for (CaseVector cv : allVectors) {
            float[] storedVector = deserializeVector(cv.getVectorJson());
            if (storedVector == null) {
                continue;
            }
            double similarity = CosineSimilarity.compute(queryVector, storedVector);
            scored.add(new ScoredVector(cv.getListingId(), similarity));
        }

        // 排序取 Top-K
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        List<ScoredVector> topK = scored.subList(0, Math.min(k, scored.size()));

        // 构建 SimilarCase，生成机械模板 reason
        return topK.stream()
                .map(sv -> buildSimilarCase(sv, listingMap, queryPhone))
                .toList();
    }

    // ======================== 内部方法 ========================

    private String buildEmbedText(Listing listing) {
        String title = listing.getTitle() != null ? listing.getTitle() : "";
        String desc = listing.getDescription() != null ? listing.getDescription() : "";
        return title + "\n" + desc;
    }

    private String serializeVector(float[] vector) {
        try {
            return OBJECT_MAPPER.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("向量 JSON 序列化失败", e);
        }
    }

    private float[] deserializeVector(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, FLOAT_ARRAY_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("向量 JSON 反序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 批量加载 Listing，按 listingId(Integer) 建 Map。
     */
    private Map<Integer, Listing> loadListingMap(List<Integer> listingIds) {
        if (listingIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Listing> listings = listingMapper.selectList(
                new LambdaQueryWrapper<Listing>()
                        .in(Listing::getId, listingIds.stream().map(Integer::longValue).toList())
        );
        return listings.stream()
                .collect(Collectors.toMap(l -> l.getId().intValue(), Function.identity()));
    }

    /**
     * 机械模板生成 reason（不允许 LLM 生成）。
     */
    private SimilarCase buildSimilarCase(ScoredVector sv, Map<Integer, Listing> listingMap, String queryPhone) {
        Listing listing = listingMap.get(sv.listingId);
        String title = listing != null ? listing.getTitle() : "未知";
        String riskLevel = listing != null && listing.getRiskLevel() != null ? listing.getRiskLevel() : "";
        String riskTags = listing != null && listing.getRiskTags() != null ? listing.getRiskTags() : "";
        String phone = listing != null ? listing.getPhone() : null;

        String reason = buildReason(sv.listingId, sv.score, phone, queryPhone);

        return new SimilarCase(sv.listingId, title, riskLevel, riskTags, sv.score, reason);
    }

    /**
     * reason 生成规则（机械模板，不涉及判断）：
     * - 相似度 ≥ 0.85 → "高度相似"
     * - 命中同 phone → "与案例 #id 联系方式相同"
     * - 否则 → "文本内容相似"
     */
    private String buildReason(int listingId, double score, String phone, String queryPhone) {
        if (score >= 0.85) {
            return "高度相似";
        }
        if (queryPhone != null && !queryPhone.isBlank()
                && phone != null && !phone.isBlank()
                && queryPhone.equals(phone)) {
            return "与案例 #" + listingId + " 联系方式相同";
        }
        return "文本内容相似";
    }

    private record ScoredVector(int listingId, double score) {
    }
}
