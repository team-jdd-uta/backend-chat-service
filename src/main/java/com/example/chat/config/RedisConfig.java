package com.example.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

@Configuration
public class RedisConfig {

    @Value("${spring.redis.cluster.nodes}")
    private List<String> redisNodes;

    @Value("${spring.redis.mode:cluster}")
    private String redisMode;

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${chat.stream.redis.host:localhost}")
    private String streamRedisHost;

    @Value("${chat.stream.redis.port:6379}")
    private int streamRedisPort;

    @Value("${spring.redis.ssl:false}")
    private boolean redisSsl;

    @Value("${chat.stream.redis.ssl:false}")
    private boolean streamRedisSsl;

    @Bean
    @Primary
    public LettuceConnectionFactory redisConnectionFactory() {
        LettuceClientConfiguration clientConfiguration = redisSsl
                ? LettuceClientConfiguration.builder().useSsl().build()
                : LettuceClientConfiguration.builder().build();

        if ("standalone".equalsIgnoreCase(redisMode)) {
            RedisStandaloneConfiguration standaloneConfiguration =
                    new RedisStandaloneConfiguration(redisHost, redisPort);
            return new LettuceConnectionFactory(standaloneConfiguration, clientConfiguration);
        }

        RedisClusterConfiguration clusterConfiguration = new RedisClusterConfiguration(redisNodes);
        clusterConfiguration.setMaxRedirects(3);
        return new LettuceConnectionFactory(clusterConfiguration, clientConfiguration);
    }

    @Bean(name = "streamRedisConnectionFactory")
    public LettuceConnectionFactory streamRedisConnectionFactory() {
        LettuceClientConfiguration clientConfiguration = streamRedisSsl
                ? LettuceClientConfiguration.builder().useSsl().build()
                : LettuceClientConfiguration.builder().build();

        RedisStandaloneConfiguration standaloneConfiguration =
                new RedisStandaloneConfiguration(streamRedisHost, streamRedisPort);
        return new LettuceConnectionFactory(standaloneConfiguration, clientConfiguration);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(jsonSerializer);
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(jsonSerializer);
        return redisTemplate;
    }

    @Bean(name = "streamStringRedisTemplate")
    public StringRedisTemplate streamStringRedisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        return new StringRedisTemplate(connectionFactory);
    }
}
