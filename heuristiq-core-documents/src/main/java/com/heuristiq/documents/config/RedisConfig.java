package com.heuristiq.documents.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis connection used solely to publish search-index events onto the shared Redis Stream that
 * 2026_RailCab_SearchAPI consumes (see search.SearchEventPublisher) -- distinct from a per-service
 * cache, this is a cross-service message bus. In any environment where BookingsAPI/AdminAPI/UAM/
 * SearchAPI also publish/consume this stream, point their Redis configs at the SAME broker as
 * SEARCH_REDIS_HOST/PORT below.
 */
@Configuration
public class RedisConfig {

    @Value("${railcab.search.redis.host:localhost}")
    private String redisHost;

    @Value("${railcab.search.redis.port:6379}")
    private int redisPort;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}
