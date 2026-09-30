package com.urlshortener.service.repository;

import com.urlshortener.service.domain.UrlDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UrlRepository extends MongoRepository<UrlDocument, String> {

    Optional<UrlDocument> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);
}
