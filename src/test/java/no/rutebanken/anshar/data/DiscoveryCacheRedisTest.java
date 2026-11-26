package no.rutebanken.anshar.data;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

/**
 * Tests the Redis persistence of {@link DiscoveryCache} against a real Redis (Testcontainers),
 * with a controllable clock to simulate the expiration of stops / lines.
 */
class DiscoveryCacheRedisTest {

    private static final Duration TTL = Duration.ofDays(30);
    private static final String STOPS_INDEX = "anshar:discovery:stops:datasets";
    private static final String LINES_INDEX = "anshar:discovery:lines:datasets";

    private static final GenericContainer<?> redis = new GenericContainer<>("redis:6.2").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private MutableClock clock;
    private DiscoveryCache discoveryCache;

    @BeforeAll
    static void startRedis() {
        redis.start();
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
        redis.stop();
    }

    @BeforeEach
    void init() {
        redisTemplate.execute(connection -> {
            connection.serverCommands().flushAll();
            return null;
        }, true);
        clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        discoveryCache = new DiscoveryCache(redisTemplate, TTL, clock);
    }

    private static String stopsKey(String datasetId) {
        return "anshar:discovery:stops:" + datasetId;
    }

    @Test
    void addDoesNotWriteToRedisUntilFlush() {
        discoveryCache.addStop("DS1", "A");
        discoveryCache.addStops("DS1", List.of("B", "C"));

        assertFalse(redisTemplate.hasKey(stopsKey("DS1")), "nothing must be sent to redis before the flush");
        assertEquals(Set.of("A", "B", "C"), discoveryCache.getDiscoveryStopsForDataset("DS1"),
                "values not yet flushed must be visible from the local buffer");
    }

    @Test
    void flushWritesValuesWithLastSeenScoreAndTtl() {
        discoveryCache.addStops("DS1", List.of("A", "B"));

        discoveryCache.flushToRedis();

        assertEquals(2L, redisTemplate.opsForZSet().zCard(stopsKey("DS1")));
        assertEquals((double) clock.millis(), redisTemplate.opsForZSet().score(stopsKey("DS1"), "A"));
        assertEquals(Set.of("DS1"), redisTemplate.opsForSet().members(STOPS_INDEX));

        Long keyTtl = redisTemplate.getExpire(stopsKey("DS1"));
        assertNotNull(keyTtl);
        assertTrue(keyTtl > 0 && keyTtl <= TTL.toSeconds(), "dataset key must have a TTL, was " + keyTtl);
        Long indexTtl = redisTemplate.getExpire(STOPS_INDEX);
        assertTrue(indexTtl != null && indexTtl > 0, "index key must have a TTL, was " + indexTtl);
    }

    @Test
    void flushedValuesAreReadFromRedisByAnotherInstance() {
        discoveryCache.addStops("DS1", List.of("A", "B"));
        discoveryCache.addLine("DS2", "L1");
        discoveryCache.flushToRedis();

        // simulates another Anshar instance, or a restart : empty local buffer
        DiscoveryCache otherInstance = new DiscoveryCache(redisTemplate, TTL, clock);

        assertEquals(Set.of("A", "B"), otherInstance.getDiscoveryStopsForDataset("DS1"));
        assertEquals(Map.of("DS1", Set.of("A", "B")), otherInstance.getDiscoveryStops());
        assertEquals(Map.of("DS2", Set.of("L1")), otherInstance.getDiscoveryLines());
    }

    @Test
    void readsMergeRedisAndLocalBuffer() {
        discoveryCache.addStop("DS1", "A");
        discoveryCache.flushToRedis();
        discoveryCache.addStop("DS1", "B");
        discoveryCache.addStop("DS2", "C");

        assertEquals(Set.of("A", "B"), discoveryCache.getDiscoveryStopsForDataset("DS1"));
        assertEquals(Map.of("DS1", Set.of("A", "B"), "DS2", Set.of("C")), discoveryCache.getDiscoveryStops());
    }

    @Test
    void readsDoNotWriteAnything() {
        discoveryCache.addStop("DS1", "A");
        discoveryCache.flushToRedis();
        Double scoreBefore = redisTemplate.opsForZSet().score(stopsKey("DS1"), "A");

        clock.advance(Duration.ofDays(1));
        Map<String, Set<String>> all = discoveryCache.getDiscoveryStops();
        all.get("DS1").add("MODIFIED_BY_CALLER");
        discoveryCache.flushToRedis();

        assertEquals(scoreBefore, redisTemplate.opsForZSet().score(stopsKey("DS1"), "A"),
                "a read must not refresh the last seen date");
        assertEquals(Set.of("A"), discoveryCache.getDiscoveryStopsForDataset("DS1"),
                "modifying the returned map must not change the cache");
    }

    @Test
    void ttlIsPerValueAndRefreshedWhenValueIsSeenAgain() {
        discoveryCache.addStops("DS1", List.of("A", "B"));
        discoveryCache.flushToRedis();

        // A is received again 20 days later, B never
        clock.advance(Duration.ofDays(20));
        discoveryCache.addStop("DS1", "A");
        discoveryCache.flushToRedis();

        // day 31 : B is expired although the dataset keeps receiving data
        clock.advance(Duration.ofDays(11));
        assertEquals(Set.of("A"), discoveryCache.getDiscoveryStopsForDataset("DS1"),
                "expired value must be ignored on read, even before the purge");
        assertEquals(2L, redisTemplate.opsForZSet().zCard(stopsKey("DS1")), "not purged yet");

        discoveryCache.flushToRedis();
        assertEquals(Set.of("A"), redisTemplate.opsForZSet().range(stopsKey("DS1"), 0, -1),
                "expired value must be purged from redis on flush, even with an empty buffer");
    }

    @Test
    void datasetIsRemovedFromIndexWhenAllItsValuesExpired() {
        discoveryCache.addStop("DS1", "A");
        discoveryCache.addStop("DS2", "B");
        discoveryCache.flushToRedis();

        clock.advance(Duration.ofDays(15));
        discoveryCache.addStop("DS2", "B");
        discoveryCache.flushToRedis();

        clock.advance(Duration.ofDays(16));
        discoveryCache.flushToRedis();

        assertEquals(Set.of("DS2"), redisTemplate.opsForSet().members(STOPS_INDEX));
        assertFalse(redisTemplate.hasKey(stopsKey("DS1")));
        assertNull(discoveryCache.getDiscoveryStopsForDataset("DS1"));
        assertEquals(Map.of("DS2", Set.of("B")), discoveryCache.getDiscoveryStops());
    }

    @Test
    void clearPurgesRedisAndLocalBufferOfItsTypeOnly() {
        discoveryCache.addStop("DS1", "A");
        discoveryCache.addLine("DS1", "L1");
        discoveryCache.flushToRedis();
        discoveryCache.addStop("DS2", "B");

        discoveryCache.clearDiscoveryStops();

        assertFalse(redisTemplate.hasKey(STOPS_INDEX));
        assertFalse(redisTemplate.hasKey(stopsKey("DS1")));
        assertTrue(discoveryCache.getDiscoveryStops().isEmpty());
        assertNull(discoveryCache.getDiscoveryStopsForDataset("DS2"), "local buffer must be cleared too");

        discoveryCache.flushToRedis();
        assertFalse(redisTemplate.hasKey(stopsKey("DS2")), "cleared buffer must not be flushed afterwards");

        assertEquals(Set.of("L1"), discoveryCache.getDiscoveryLinesForDataset("DS1"), "lines must not be cleared");
        assertTrue(redisTemplate.hasKey(LINES_INDEX));
    }

    @Test
    void failedFlushKeepsDataAndRetriesOnNextFlush() {
        StringRedisTemplate failingOnceTemplate = spy(redisTemplate);
        doThrow(new RedisConnectionFailureException("redis down"))
                .doCallRealMethod()
                .when(failingOnceTemplate).opsForZSet();
        DiscoveryCache cache = new DiscoveryCache(failingOnceTemplate, TTL, clock);

        cache.addStop("DS1", "A");
        cache.flushToRedis();   // fails : must not throw, data goes back to the buffer

        assertFalse(redisTemplate.hasKey(stopsKey("DS1")));
        assertEquals(Set.of("A"), cache.getDiscoveryStopsForDataset("DS1"), "data must not be lost");

        cache.flushToRedis();   // retry succeeds

        assertEquals(Set.of("A"), redisTemplate.opsForZSet().range(stopsKey("DS1"), 0, -1));
        assertEquals(Set.of("DS1"), redisTemplate.opsForSet().members(STOPS_INDEX));
    }

    @Test
    void readsFallBackToLocalBufferWhenRedisIsUnavailable() {
        StringRedisTemplate failingTemplate = spy(redisTemplate);
        doThrow(new RedisConnectionFailureException("redis down")).when(failingTemplate).opsForZSet();
        doThrow(new RedisConnectionFailureException("redis down")).when(failingTemplate).opsForSet();
        DiscoveryCache cache = new DiscoveryCache(failingTemplate, TTL, clock);

        cache.addStop("DS1", "A");

        assertEquals(Set.of("A"), cache.getDiscoveryStopsForDataset("DS1"));
        assertEquals(Map.of("DS1", Set.of("A")), cache.getDiscoveryStops());
    }

    @Test
    void unknownOrNullDatasetReturnsNull() {
        assertNull(discoveryCache.getDiscoveryStopsForDataset("UNKNOWN"));
        assertNull(discoveryCache.getDiscoveryLinesForDataset(null));
        assertTrue(discoveryCache.getDiscoveryLines().isEmpty());
    }


    /**
     * Clock whose time can be moved forward by the test.
     */
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
