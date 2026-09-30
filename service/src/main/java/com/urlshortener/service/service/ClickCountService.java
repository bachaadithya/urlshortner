package com.urlshortener.service.service;

import com.urlshortener.service.domain.UrlDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Atomic click increments via MongoDB {@code $inc} so concurrent redirects stay thread-safe.
 */
@Service
public class ClickCountService {

    private static final Logger log = LoggerFactory.getLogger(ClickCountService.class);

    private final MongoTemplate mongoTemplate;

    public ClickCountService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Async
    public void incrementAsync(String shortCode) {
        try {
            Query query = Query.query(Criteria.where("shortCode").is(shortCode));
            Update update = new Update().inc("clickCount", 1);
            mongoTemplate.updateFirst(query, update, UrlDocument.class);
        } catch (Exception ex) {
            log.warn("Failed to increment click count for {}", shortCode, ex);
        }
    }
}
