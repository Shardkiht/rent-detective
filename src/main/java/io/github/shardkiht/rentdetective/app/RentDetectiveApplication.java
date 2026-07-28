package io.github.shardkiht.rentdetective.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "io.github.shardkiht.rentdetective")
@EnableAsync
@MapperScan({
        "io.github.shardkiht.rentdetective.domain.mapper",
        "io.github.shardkiht.rentdetective.rag",
        "io.github.shardkiht.rentdetective.rules.relation"
})
public class RentDetectiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(RentDetectiveApplication.class, args);
    }
}
