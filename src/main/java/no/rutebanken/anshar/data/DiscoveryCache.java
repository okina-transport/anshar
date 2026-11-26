package no.rutebanken.anshar.data;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Discovery cache (stops / lines per dataset), persisted in Redis.
 * <p>
 * addXxx methods only feed a local in-memory buffer (cheap, called very often).
 * The buffer is periodically flushed to Redis (one Redis sorted set per dataset). The score of each stop / line
 * is the timestamp of its last flush, which gives a TTL per stop / line : expired values are ignored on read
 * and purged on each flush.
 * getXxx methods read Redis and merge the local buffer not yet flushed.
 * <p>
 * Redis layout :
 * <ul>
 *     <li>{@code anshar:discovery:<type>:datasets} : set of known datasetIds</li>
 *     <li>{@code anshar:discovery:<type>:<datasetId>} : sorted set of stops / lines of the dataset (score = last seen epoch ms)</li>
 * </ul>
 */
@Service
public class DiscoveryCache {

    private static final Logger logger = LoggerFactory.getLogger(DiscoveryCache.class);

    private static final String KEY_PREFIX = "anshar:discovery:";

    private final BufferedRedisSetStore stops;
    private final BufferedRedisSetStore lines;

    @Autowired
    public DiscoveryCache(@Qualifier("discoveryRedisTemplate") StringRedisTemplate redisTemplate,
                          @Value("${anshar.discovery.cache.redis.ttl:P30D}") Duration ttl) {
        this(redisTemplate, ttl, Clock.systemUTC());
    }

    /**
     * Visible for tests : allows to control time (expiration of stops / lines).
     */
    DiscoveryCache(StringRedisTemplate redisTemplate, Duration ttl, Clock clock) {
        this.stops = new BufferedRedisSetStore("stops", redisTemplate, ttl, clock);
        this.lines = new BufferedRedisSetStore("lines", redisTemplate, ttl, clock);
    }

    public void addStop(String datasetId, String stop) {
        stops.add(datasetId, List.of(stop));
    }

    public void addStops(String datasetId, List<String> stops) {
        this.stops.add(datasetId, stops);
    }

    public void addLine(String datasetId, String line) {
        lines.add(datasetId, List.of(line));
    }

    public void addLines(String datasetId, List<String> lines) {
        this.lines.add(datasetId, lines);
    }

    public void clearDiscoveryStops() {
        stops.clear();
    }

    public void clearDiscoveryLines() {
        lines.clear();
    }

    public Map<String, Set<String>> getDiscoveryStops() {
        return stops.getAll();
    }

    public Map<String, Set<String>> getDiscoveryLines() {
        return lines.getAll();
    }

    public Set<String> getDiscoveryStopsForDataset(String datasetId) {
        return stops.get(datasetId);
    }

    public Set<String> getDiscoveryLinesForDataset(String datasetId) {
        return lines.get(datasetId);
    }

    /**
     * Sends the local buffers to Redis.
     */
    @Scheduled(fixedDelayString = "${anshar.discovery.cache.flush.interval:PT30M}",
            initialDelayString = "${anshar.discovery.cache.flush.interval:PT30M}")
    public void flushToRedis() {
        stops.flush();
        lines.flush();
    }

    @PreDestroy
    public void flushOnShutdown() {
        flushToRedis();
    }


    /**
     * Local buffer of datasetId -> values, flushed to Redis sets.
     */
    private static final class BufferedRedisSetStore {

        private final String type;
        private final String indexKey;
        private final StringRedisTemplate redisTemplate;
        private final Duration ttl;
        private final Clock clock;

        private final ReadWriteLock bufferLock = new ReentrantReadWriteLock();
        private ConcurrentHashMap<String, Set<String>> buffer = new ConcurrentHashMap<>();

        BufferedRedisSetStore(String type, StringRedisTemplate redisTemplate, Duration ttl, Clock clock) {
            this.type = type;
            this.indexKey = KEY_PREFIX + type + ":datasets";
            this.redisTemplate = redisTemplate;
            this.ttl = ttl;
            this.clock = clock;
        }

        private String datasetKey(String datasetId) {
            return KEY_PREFIX + type + ":" + datasetId;
        }

        /**
         * Adders share the read lock (concurrent adds allowed) : only the buffer swap done by flush/clear is exclusive.
         */
        void add(String datasetId, Collection<String> values) {
            if (datasetId == null || values == null || values.isEmpty()) {
                return;
            }
            bufferLock.readLock().lock();
            try {
                buffer.computeIfAbsent(datasetId, k -> ConcurrentHashMap.newKeySet()).addAll(values);
            } finally {
                bufferLock.readLock().unlock();
            }
        }

        private Map<String, Set<String>> drainBuffer() {
            bufferLock.writeLock().lock();
            try {
                Map<String, Set<String>> drained = buffer;
                buffer = new ConcurrentHashMap<>();
                return drained;
            } finally {
                bufferLock.writeLock().unlock();
            }
        }

        /**
         * Each value is stored in a sorted set whose score is the timestamp (epoch ms) of its last flush :
         * this gives a TTL per stop / line (Redis has no per-member TTL on sets).
         */
        void flush() {
            Map<String, Set<String>> toFlush = drainBuffer();
            long now = clock.millis();

            if (!toFlush.isEmpty()) {
                try {
                    for (Map.Entry<String, Set<String>> entry : toFlush.entrySet()) {
                        String key = datasetKey(entry.getKey());
                        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
                        entry.getValue().forEach(value -> tuples.add(ZSetOperations.TypedTuple.of(value, (double) now)));
                        redisTemplate.opsForZSet().add(key, tuples);
                        // safety net : the whole dataset disappears if it receives nothing during the TTL
                        redisTemplate.expire(key, ttl);
                    }
                    redisTemplate.opsForSet().add(indexKey, toFlush.keySet().toArray(new String[0]));
                    redisTemplate.expire(indexKey, ttl);
                    logger.info("Discovery {} flushed to redis for {} dataset(s)", type, toFlush.size());
                } catch (Exception e) {
                    logger.error("Unable to flush discovery {} to redis, will retry on next flush", type, e);
                    // put back the data in the buffer so that it is not lost (ZADD only updates the score)
                    toFlush.forEach(this::add);
                    return;
                }
            }


            purgeExpiredValues(now);
        }

        /**
         * Removes the stops / lines not received since more than the TTL, and the datasets that became empty.
         */
        private void purgeExpiredValues(long now) {
            try {
                Set<String> datasetIds = redisTemplate.opsForSet().members(indexKey);
                if (datasetIds == null) {
                    return;
                }
                long removedCount = 0;
                for (String datasetId : datasetIds) {
                    String key = datasetKey(datasetId);
                    Long removed = redisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, expirationThreshold(now));
                    removedCount += removed == null ? 0 : removed;
                    Long remaining = redisTemplate.opsForZSet().zCard(key);
                    if (remaining == null || remaining == 0) {
                        redisTemplate.opsForSet().remove(indexKey, datasetId);
                    }
                }
                if (removedCount > 0) {
                    logger.info("Discovery {} : {} expired value(s) purged from redis", type, removedCount);
                }
            } catch (Exception e) {
                logger.error("Unable to purge expired discovery {} in redis", type, e);
            }
        }

        /**
         * Values whose score is strictly lower than this threshold are expired.
         */
        private double expirationThreshold(long now) {
            return (double) (now - ttl.toMillis());
        }

        /**
         * Reads the non expired values of a dataset (expired values may still be present until the next purge).
         */
        private Set<String> readValidValues(String datasetId) {
            return redisTemplate.opsForZSet().rangeByScore(datasetKey(datasetId),
                    expirationThreshold(clock.millis()), Double.POSITIVE_INFINITY);
        }

        void clear() {
            drainBuffer();
            try {
                Set<String> datasetIds = redisTemplate.opsForSet().members(indexKey);
                Set<String> keysToDelete = new HashSet<>();
                keysToDelete.add(indexKey);
                if (datasetIds != null) {
                    datasetIds.forEach(datasetId -> keysToDelete.add(datasetKey(datasetId)));
                }
                redisTemplate.delete(keysToDelete);
            } catch (Exception e) {
                logger.error("Unable to clear discovery {} in redis", type, e);
            }
        }

        Map<String, Set<String>> getAll() {
            Map<String, Set<String>> result = new HashMap<>();
            try {
                Set<String> datasetIds = redisTemplate.opsForSet().members(indexKey);
                if (datasetIds != null) {
                    for (String datasetId : datasetIds) {
                        Set<String> values = readValidValues(datasetId);
                        if (values != null && !values.isEmpty()) {
                            result.put(datasetId, new HashSet<>(values));
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Unable to read discovery {} from redis, only local data will be returned", type, e);
            }
            buffer.forEach((datasetId, values) -> result.computeIfAbsent(datasetId, k -> new HashSet<>()).addAll(values));
            return result;
        }

        Set<String> get(String datasetId) {
            if (datasetId == null) {
                return null;
            }
            Set<String> result = new HashSet<>();
            try {
                Set<String> values = readValidValues(datasetId);
                if (values != null) {
                    result.addAll(values);
                }
            } catch (Exception e) {
                logger.error("Unable to read discovery {} from redis for dataset {}, only local data will be returned", type, datasetId, e);
            }
            Set<String> local = buffer.get(datasetId);
            if (local != null) {
                result.addAll(local);
            }
            return result.isEmpty() ? null : result;
        }
    }
}
