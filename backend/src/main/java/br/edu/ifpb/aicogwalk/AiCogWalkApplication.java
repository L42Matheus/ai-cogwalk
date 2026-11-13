package br.edu.ifpb.aicogwalk;

import br.edu.ifpb.aicogwalk.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
public class AiCogWalkApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiCogWalkApplication.class, args);
    }
}
