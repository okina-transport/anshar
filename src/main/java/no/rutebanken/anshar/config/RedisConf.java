package no.rutebanken.anshar.config;


import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.awt.print.Book;

@Configuration
public class RedisConf {

    @Value("${spring.data.redis.password}")
    String password;

    @Value("${spring.redis.host}")
    String host;

    @Value("${spring.redis.port}")
    int port;

    @Bean(name = "customTemplate")
    public RedisTemplate<Long, Book> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<Long, Book> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setValueSerializer(new StringRedisSerializer());

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

    static class JsonRedisSerializer implements RedisSerializer<Object> {

        private final ObjectMapper om;

        public JsonRedisSerializer() {
            this.om = new ObjectMapper().enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        }

        @Override
        public byte[] serialize(Object t) throws SerializationException {
            try {
                return om.writeValueAsBytes(t);
            } catch (JsonProcessingException e) {
                throw new SerializationException(e.getMessage(), e);
            }
        }

        @Override
        public Object deserialize(byte[] bytes) throws SerializationException {

            if (bytes == null) {
                return null;
            }

            try {
                return om.readValue(bytes, Object.class);
            } catch (Exception e) {
                throw new SerializationException(e.getMessage(), e);
            }
        }
    }


}
