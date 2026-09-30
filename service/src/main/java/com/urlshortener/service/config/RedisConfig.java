package com.urlshortener.service.config;

import com.urlshortener.service.bloom.BloomFilterService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer bloomReloadListener(
            RedisConnectionFactory connectionFactory,
            BloomFilterService bloomFilterService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                (message, pattern) -> bloomFilterService.reloadFromRedis(),
                new ChannelTopic(BloomFilterService.BLOOM_CHANNEL));
        return container;
    }
}
