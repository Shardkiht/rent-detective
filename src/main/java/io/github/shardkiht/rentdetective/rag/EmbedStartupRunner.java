package io.github.shardkiht.rentdetective.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时自动嵌入开关。
 * 由配置项 rag.embed-on-startup 控制，默认 false。
 */
@Component
public class EmbedStartupRunner implements CommandLineRunner {

    @Value("${rag.embed-on-startup:false}")
    private boolean embedOnStartup;

    private final CaseVectorService caseVectorService;

    public EmbedStartupRunner(CaseVectorService caseVectorService) {
        this.caseVectorService = caseVectorService;
    }

    @Override
    public void run(String... args) {
        if (embedOnStartup) {
            caseVectorService.embedAll();
        }
    }
}
