package io.github.shardkiht.rentdetective.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "io.github.shardkiht.rentdetective")
@EnableAsync
@MapperScan({
        "io.github.shardkiht.rentdetective.app.mapper",
        "io.github.shardkiht.rentdetective.app.eval.mapper",
        "io.github.shardkiht.rentdetective.rag",
        "io.github.shardkiht.rentdetective.semantic.mapper",
        "io.github.shardkiht.rentdetective.semantic.relation"
})
public class RentDetectiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(RentDetectiveApplication.class, args);
    }
}
