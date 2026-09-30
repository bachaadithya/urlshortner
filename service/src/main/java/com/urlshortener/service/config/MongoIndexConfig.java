package com.urlshortener.service.config;

import com.urlshortener.service.domain.UrlDocument;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Configuration
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    public MongoIndexConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void ensureIndexes() {
        // Unique index is the atomicity guarantee for concurrent custom-alias claims.
        mongoTemplate.indexOps(UrlDocument.class)
                .ensureIndex(new Index().on("shortCode", Sort.Direction.ASC).unique());
    }
}
