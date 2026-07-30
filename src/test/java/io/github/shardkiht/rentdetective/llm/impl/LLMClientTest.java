package io.github.shardkiht.rentdetective.llm.impl;

import io.github.shardkiht.rentdetective.llm.ChatRequest;
import io.github.shardkiht.rentdetective.llm.ChatResponse;
import io.github.shardkiht.rentdetective.llm.Message;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * LLM 客户端集成测试。
 * 运行前确保本地 Ollama 服务已启动，或百炼平台 api-key 已配置。
 * 配置读取自 application.yml → application-dev.yml。
 */
@SpringBootTest(classes = LLMClientTest.Config.class)
class LLMClientTest {

    private static final Logger log = LoggerFactory.getLogger(LLMClientTest.class);

    /**
     * 仅加载 LLM 相关 Bean，避免触发 Mapper/Controller 等无关依赖
     */
    @Configuration
    @ComponentScan(basePackages = "io.github.shardkiht.rentdetective.llm")
    static class Config {
    }

    @Autowired
    private OllamaLLMClient ollamaClient;

    @Autowired
    private OpenAiCompatibleLLMClient cloudClient;

    private ChatRequest buildRequest() {
        return new ChatRequest(
                List.of(
                        Message.system("你是一个租房风险分析助手。"),
                        Message.user("这套房源描述有猫腻吗？位于北京朝阳区，两室一厅，月租1500元。")
                ),
                List.of()
        );
    }

    @Test
    void ollamaChat() {
        ChatResponse response = ollamaClient.chat(buildRequest());
        log.info("[Ollama] content={}, hasToolCall={}",
                response.content(), response.hasToolCall());
    }

    @Test
    void cloudChat() {
        ChatResponse response = cloudClient.chat(buildRequest());
        log.info("[百炼] content={}, hasToolCall={}",
                response.content(), response.hasToolCall());
    }
}
