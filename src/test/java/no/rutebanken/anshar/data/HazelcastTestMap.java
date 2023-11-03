package no.rutebanken.anshar.data;

import com.hazelcast.aggregation.Aggregator;
import com.hazelcast.config.IndexConfig;
import com.hazelcast.config.IndexType;
import com.hazelcast.core.EntryView;
import com.hazelcast.map.*;
import com.hazelcast.map.listener.MapListener;
import com.hazelcast.map.listener.MapPartitionLostListener;
import com.hazelcast.projection.Projection;
import com.hazelcast.query.Predicate;
import com.hazelcast.spi.tenantcontrol.DestroyEventContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HazelcastTestMap<T> implements IMap {


    private Map<SiriObjectStorageKey, T> map = new HashMap();

    @Override
    public void putAll(Map m) {

    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return false;
    }

    @Override
    public Object get(Object key) {
        return map.get(key);
    }


    @Override
    public Object put(@Nonnull Object key, @Nonnull Object value) {
        map.put((SiriObjectStorageKey) key, (T) value);
        return value;
    }


    @Override
    public Object remove(Object key) {
        return null;
    }

    @Override
    public boolean remove(Object key, Object value) {
        return false;
    }

    @Override
    public void removeAll(@Nonnull Predicate predicate) {

    }

    @Override
    public void delete(@Nonnull Object key) {

    }

    @Override
    public void flush() {

    }

    @Override
    public Map getAll(@Nullable Set keys) {
        Map results = new HashMap();
        map.entrySet().stream().filter(
                        entry -> keys.contains(entry.getKey()))
                .forEach(entry -> results.put(entry.getKey(), entry.getValue()));

        return results;
    }


    @Override
    public void loadAll(boolean replaceExistingValues) {

    }

    @Override
    public void loadAll(@Nonnull Set keys, boolean replaceExistingValues) {

    }

    @Override
    public void clear() {

    }

    @Override
    public CompletionStage getAsync(@Nonnull Object key) {
        return null;
    }

    @Override
    public CompletionStage putAsync(@Nonnull Object key, @Nonnull Object value) {
        return null;
    }

    @Override
    public CompletionStage putAsync(@Nonnull Object key, @Nonnull Object value, long ttl, @Nonnull TimeUnit ttlUnit) {
        return null;
    }

    @Override
    public CompletionStage putAsync(@Nonnull Object key, @Nonnull Object value, long ttl, @Nonnull TimeUnit ttlUnit, long maxIdle, @Nonnull TimeUnit maxIdleUnit) {
        return null;
    }

    @Override
    public CompletionStage<Void> putAllAsync(@Nonnull Map map) {
        return null;
    }

    @Override
    public CompletionStage<Void> setAsync(@Nonnull Object key, @Nonnull Object value) {
        return null;
    }

    @Override
    public CompletionStage<Void> setAsync(@Nonnull Object key, @Nonnull Object value, long ttl, @Nonnull TimeUnit ttlUnit) {
        return null;
    }

    @Override
    public CompletionStage<Void> setAsync(@Nonnull Object key, @Nonnull Object value, long ttl, @Nonnull TimeUnit ttlUnit, long maxIdle, @Nonnull TimeUnit maxIdleUnit) {
        return null;
    }

    @Override
    public CompletionStage removeAsync(@Nonnull Object key) {
        return null;
    }

    @Override
    public boolean tryRemove(@Nonnull Object key, long timeout, @Nonnull TimeUnit timeunit) {
        return false;
    }

    @Override
    public boolean tryPut(@Nonnull Object key, @Nonnull Object value, long timeout, @Nonnull TimeUnit timeunit) {
        return false;
    }

    @Override
    public Object put(@Nonnull Object key, @Nonnull Object value, long ttl, @Nonnull TimeUnit ttlUnit) {
        return null;
    }

    @Override
    public Object put(@Nonnull Object key, @Nonnull Object value, long ttl, @Nonnull TimeUnit ttlUnit, long maxIdle, @Nonnull TimeUnit maxIdleUnit) {
        return null;
    }

    @Override
    public void putTransient(@Nonnull Object key, @Nonnull Object value, long ttl, @Nonnull TimeUnit ttlUnit) {

    }

    @Override
    public void putTransient(@Nonnull Object key, @Nonnull Object value, long ttl, @Nonnull TimeUnit ttlUnit, long maxIdle, @Nonnull TimeUnit maxIdleUnit) {

    }

    @Override
    public Object putIfAbsent(@Nonnull Object key, @Nonnull Object value) {
        return null;
    }

    @Override
    public Object putIfAbsent(@Nonnull Object key, @Nonnull Object value, long ttl, @Nonnull TimeUnit ttlUnit) {
        return null;
    }

    @Override
    public Object putIfAbsent(@Nonnull Object key, @Nonnull Object value, long ttl, @Nonnull TimeUnit ttlUnit, long maxIdle, @Nonnull TimeUnit maxIdleUnit) {
        return null;
    }

    @Override
    public boolean replace(@Nonnull Object key, @Nonnull Object oldValue, @Nonnull Object newValue) {
        return false;
    }

    @Override
    public Object replace(@Nonnull Object key, @Nonnull Object value) {
        return null;
    }

    @Override
    public void set(@Nonnull Object key, @Nonnull Object value) {
        map.put((SiriObjectStorageKey) key, (T) value);
    }

    @Override
    public void set(@Nonnull Object key, @Nonnull Object value, long ttl, @Nonnull TimeUnit ttlUnit) {
        map.put((SiriObjectStorageKey) key, (T) value);
    }

    @Override
    public void set(@Nonnull Object key, @Nonnull Object value, long ttl, @Nonnull TimeUnit ttlUnit, long maxIdle, @Nonnull TimeUnit maxIdleUnit) {
        map.put((SiriObjectStorageKey) key, (T) value);
    }


    @Override
    public void setAll(@Nonnull Map map) {

    }

    @Override
    public CompletionStage<Void> setAllAsync(@Nonnull Map map) {
        return null;
    }

    @Override
    public void lock(@Nonnull Object key) {

    }

    @Override
    public void lock(@Nonnull Object key, long leaseTime, @Nullable TimeUnit timeUnit) {

    }

    @Override
    public boolean isLocked(@Nonnull Object key) {
        return false;
    }

    @Override
    public boolean tryLock(@Nonnull Object key) {
        return false;
    }

    @Override
    public boolean tryLock(@Nonnull Object key, long time, @Nullable TimeUnit timeunit) throws InterruptedException {
        return false;
    }

    @Override
    public boolean tryLock(@Nonnull Object key, long time, @Nullable TimeUnit timeunit, long leaseTime, @Nullable TimeUnit leaseTimeunit) throws InterruptedException {
        return false;
    }

    @Override
    public void unlock(@Nonnull Object key) {

    }

    @Override
    public void forceUnlock(@Nonnull Object key) {

    }

    @Override
    public UUID addLocalEntryListener(@Nonnull MapListener listener) {
        return null;
    }

    @Override
    public UUID addLocalEntryListener(@Nonnull MapListener listener, @Nonnull Predicate predicate, boolean includeValue) {
        return null;
    }

    @Override
    public UUID addLocalEntryListener(@Nonnull MapListener listener, @Nonnull Predicate predicate, @Nullable Object key, boolean includeValue) {
        return null;
    }

    @Override
    public String addInterceptor(@Nonnull MapInterceptor interceptor) {
        return null;
    }

    @Override
    public boolean removeInterceptor(@Nonnull String id) {
        return false;
    }

    @Override
    public UUID addEntryListener(@Nonnull MapListener listener, boolean includeValue) {
        return null;
    }

    @Override
    public boolean removeEntryListener(@Nonnull UUID id) {
        return false;
    }

    @Override
    public UUID addPartitionLostListener(@Nonnull MapPartitionLostListener listener) {
        return null;
    }

    @Override
    public boolean removePartitionLostListener(@Nonnull UUID id) {
        return false;
    }

    @Override
    public UUID addEntryListener(@Nonnull MapListener listener, @Nonnull Object key, boolean includeValue) {
        return null;
    }

    @Override
    public UUID addEntryListener(@Nonnull MapListener listener, @Nonnull Predicate predicate, boolean includeValue) {
        return null;
    }

    @Override
    public UUID addEntryListener(@Nonnull MapListener listener, @Nonnull Predicate predicate, @Nullable Object key, boolean includeValue) {
        return null;
    }

    @Override
    public EntryView getEntryView(@Nonnull Object key) {
        return null;
    }

    @Override
    public boolean evict(@Nonnull Object key) {
        return false;
    }

    @Override
    public void evictAll() {

    }

    @Nonnull
    @Override
    public Set keySet() {
        return null;
    }

    @Nonnull
    @Override
    public Collection values() {
        return map.values();
    }

    @Nonnull
    @Override
    public Set<Entry> entrySet() {
        return null;
    }

    @Override
    public Set keySet(@Nonnull Predicate predicate) {
        return map.entrySet().stream().filter(
                        key -> predicate.apply(key))
                .map(Entry::getKey)
                .collect(Collectors.toSet());


    }

    @Override
    public Set<Entry> entrySet(@Nonnull Predicate predicate) {
        return null;
    }

    @Override
    public Collection values(@Nonnull Predicate predicate) {
        return null;
    }

    @Override
    public Set localKeySet() {
        return null;
    }

    @Override
    public Set localKeySet(@Nonnull Predicate predicate) {
        return null;
    }

    @Override
    public void addIndex(IndexType type, String... attributes) {
        IMap.super.addIndex(type, attributes);
    }

    @Override
    public void addIndex(IndexConfig indexConfig) {

    }

    @Override
    public LocalMapStats getLocalMapStats() {
        return null;
    }

    @Override
    public QueryCache getQueryCache(@Nonnull String name) {
        return null;
    }

    @Override
    public QueryCache getQueryCache(@Nonnull String name, @Nonnull Predicate predicate, boolean includeValue) {
        return null;
    }

    @Override
    public QueryCache getQueryCache(@Nonnull String name, @Nonnull MapListener listener, @Nonnull Predicate predicate, boolean includeValue) {
        return null;
    }

    @Override
    public boolean setTtl(@Nonnull Object key, long ttl, @Nonnull TimeUnit timeunit) {
        return false;
    }

    @Override
    public Object computeIfPresent(@Nonnull Object key, @Nonnull BiFunction remappingFunction) {
        return null;
    }

    @Override
    public Object computeIfAbsent(@Nonnull Object key, @Nonnull Function mappingFunction) {
        return null;
    }

    @Override
    public Object getOrDefault(Object key, Object defaultValue) {
        return IMap.super.getOrDefault(key, defaultValue);
    }

    @Override
    public void forEach(@Nonnull BiConsumer action) {
        IMap.super.forEach(action);
    }

    @Override
    public Object compute(@Nonnull Object key, @Nonnull BiFunction remappingFunction) {
        return null;
    }

    @Override
    public Object merge(@Nonnull Object key, @Nonnull Object value, @Nonnull BiFunction remappingFunction) {
        return null;
    }

    @Override
    public void replaceAll(@Nonnull BiFunction function) {
        IMap.super.replaceAll(function);
    }

    @Nonnull
    @Override
    public Iterator<Entry> iterator() {
        return null;
    }

    @Override
    public void forEach(Consumer action) {
        IMap.super.forEach(action);
    }

    @Override
    public Spliterator spliterator() {
        return IMap.super.spliterator();
    }

    @Nonnull
    @Override
    public Iterator<Entry> iterator(int fetchSize) {
        return null;
    }

    @Override
    public Collection project(@Nonnull Projection projection, @Nonnull Predicate predicate) {
        return null;
    }

    @Override
    public Collection project(@Nonnull Projection projection) {
        return null;
    }

    @Override
    public Object aggregate(@Nonnull Aggregator aggregator, @Nonnull Predicate predicate) {
        return null;
    }

    @Override
    public Object aggregate(@Nonnull Aggregator aggregator) {
        return null;
    }

    @Override
    public Map executeOnEntries(@Nonnull EntryProcessor entryProcessor, @Nonnull Predicate predicate) {
        return null;
    }

    @Override
    public Map executeOnEntries(@Nonnull EntryProcessor entryProcessor) {
        return null;
    }

    @Override
    public CompletionStage submitToKey(@Nonnull Object key, @Nonnull EntryProcessor entryProcessor) {
        return null;
    }

    @Override
    public CompletionStage<Map> submitToKeys(@Nonnull Set keys, @Nonnull EntryProcessor entryProcessor) {
        return null;
    }

    @Override
    public Map executeOnKeys(@Nonnull Set keys, @Nonnull EntryProcessor entryProcessor) {
        return null;
    }

    @Override
    public Object executeOnKey(@Nonnull Object key, @Nonnull EntryProcessor entryProcessor) {
        return null;
    }


    @Override
    public String getPartitionKey() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public String getServiceName() {
        return null;
    }

    @Override
    public void destroy() {

    }

    @Nonnull
    @Override
    public DestroyEventContext getDestroyContextForTenant() {
        return IMap.super.getDestroyContextForTenant();
    }
}