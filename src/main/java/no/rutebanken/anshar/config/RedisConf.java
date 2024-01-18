package no.rutebanken.anshar.config;


import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.awt.print.Book;

@Configuration
public class RedisConf {

    @Value("${spring.data.redis.password}")
    String password;

    @Value("${spring.redis.host}")
    String host;

    @Value("${spring.redis.port}")
    int port;

    @Bean
    public RedisTemplate<Long, Book> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<Long, Book> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        // Add some specific configuration here. Key serializers, etc.
        return template;
    }

    @Bean(name = "customRedisConnectionFactory")
    RedisConnectionFactory redisconnectionfactory(RedisProperties props) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        if (StringUtils.isNotEmpty(password)) {
            config.setPassword(password);
        }

        config.setHostName(host);
        config.setPort(port);


        return new LettuceConnectionFactory(config);
    }


}
