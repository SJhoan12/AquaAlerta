package com.aquaalerta.aquaalerta.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Configuration
public class MongoConfig {

    @Bean
    public ApplicationRunner initIndexes(MongoTemplate mongoTemplate) {
        return args -> {
            mongoTemplate.indexOps("lecturasNivel")
                    .ensureIndex(new Index()
                            .on("tanqueId", Sort.Direction.ASC)
                            .on("timestamp", Sort.Direction.DESC)
                            .named("idx_lecturas_tanque_timestamp"));

            mongoTemplate.indexOps("alertas")
                    .ensureIndex(new Index()
                            .on("estado", Sort.Direction.ASC)
                            .named("idx_alertas_estado"));
        };
    }
}
