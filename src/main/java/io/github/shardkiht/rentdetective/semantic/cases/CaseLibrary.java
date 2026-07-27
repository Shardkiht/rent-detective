package io.github.shardkiht.rentdetective.semantic.cases;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.shardkiht.rentdetective.rag.CaseVectorService;
import io.github.shardkiht.rentdetective.rag.SimilarCase;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 案例库。委托 CaseVectorService 进行 RAG 相似案例检索。
 */
@Component
public class CaseLibrary {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_TOP_K = 5;

    private final CaseVectorService caseVectorService;

    public CaseLibrary(CaseVectorService caseVectorService) {
        this.caseVectorService = caseVectorService;
    }

    /**
     * 查找与描述最相似的案例，返回 JSON 字符串。
     *
     * @param description 房源描述文本
     * @return 相似案例列表的 JSON 字符串
     */
    public String findSimilar(String description) {
        List<SimilarCase> cases = caseVectorService.search(description, DEFAULT_TOP_K);
        try {
            return OBJECT_MAPPER.writeValueAsString(cases);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
