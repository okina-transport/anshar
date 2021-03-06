/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.data;

import com.hazelcast.core.IMap;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.metrics.PrometheusMetricsService;
import no.rutebanken.anshar.routes.siri.transformer.ApplicationContextHolder;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

abstract class SiriRepository<T> {

    private IMap<String, Instant> lastUpdateRequested;
    private IMap<String, Set<String>> changesMap;

    abstract Collection<T> getAll();

    abstract int getSize();

    abstract Collection<T> getAll(String datasetId);

    abstract Collection<T> getAllUpdates(String requestorId, String datasetId);

    abstract Collection<T> addAll(String datasetId, List<T> ptList);

    abstract T add(String datasetId, T timetableDelivery);

    abstract long getExpiration(T s);

    private final Logger logger = LoggerFactory.getLogger(SiriRepository.class);

    private PrometheusMetricsService metrics;

    final Set<String> dirtyChanges = Collections.synchronizedSet(new HashSet<>());

    private ScheduledExecutorService singleThreadScheduledExecutor;

    void initBufferCommitter(ExtendedHazelcastService hazelcastService, IMap<String, Instant> lastUpdateRequested, IMap<String, Set<String>> changesMap, int commitFrequency) {
        this.lastUpdateRequested = lastUpdateRequested;
        this.changesMap = changesMap;

        if (singleThreadScheduledExecutor == null) {
            singleThreadScheduledExecutor = Executors.newSingleThreadScheduledExecutor();

            logger.info("Initializing scheduled change-buffer-updater with commit every {} seconds", commitFrequency);

            singleThreadScheduledExecutor.scheduleWithFixedDelay(() -> {
                commitChanges();
            }, 0, commitFrequency, TimeUnit.SECONDS);
        }

        hazelcastService.addBeforeShuttingDownHook(() -> {
            while (!dirtyChanges.isEmpty()) {
                commitChanges();
            }
        });
    }

    /**
     * Commits local change-buffer to cluster
     */
    void commitChanges() {

        try {
            if (!dirtyChanges.isEmpty()) {

                long t1 = System.currentTimeMillis();

                final Set<String> bufferedChanges = new HashSet<>(dirtyChanges);
                dirtyChanges.clear();

                changesMap.keySet().forEach(key -> {
                    if (!lastUpdateRequested.containsKey(key)) {
                        changesMap.delete(key);
                    }
                });

                changesMap.executeOnEntries(new AppendChangesToSetEntryProcessor(bufferedChanges));
                logger.info("Updating changes for {} requestors ({}), committed {} changes, update took {} ms",
                        changesMap.size(), this.getClass().getSimpleName(), bufferedChanges.size(), (System.currentTimeMillis() - t1));
            } else {
                logger.debug("No changes - ignoring commit ({})", this.getClass().getSimpleName());
            }
        } catch (Throwable t) {
            //Catch everything to avoid executor being killed
            logger.info("Exception caught when comitting changes", t);
        }
    }


    /**
     * Adds ids to local change-buffer
     * @param changes
     */
    void markIdsAsUpdated(Set<String> changes) {
        if (!changes.isEmpty()) {
            dirtyChanges.addAll(changes);
            logger.info("Added {} updates to {} dirty-buffer, now has {} pending updates", changes.size(), this.getClass().getSimpleName(), dirtyChanges.size());
        }
    }

    void markDataReceived(SiriDataType dataType, String datasetId, long totalSize, long updatedSize, long expiredSize, long ignoredSize) {
        if (metrics == null) {
            metrics = ApplicationContextHolder.getContext().getBean(PrometheusMetricsService.class);
        }
        metrics.registerIncomingData(dataType, datasetId, totalSize, updatedSize, expiredSize, ignoredSize);
    }

    void updateChangeTrackers(IMap<String, Instant> lastUpdateRequested, IMap<String, Set<String>> changesMap, String key, Set<String> changes, int trackingPeriodMinutes, TimeUnit timeUnit) {
        long t1 = System.currentTimeMillis();

        changesMap.executeOnKey(key, new ReplaceSetEntryProcessor(changes));
        changesMap.setTtl(key, trackingPeriodMinutes, timeUnit);

        lastUpdateRequested.set(key, Instant.now(), trackingPeriodMinutes, timeUnit);

        logger.info("Replacing changes for requestor {} took {} ms. ({})", key, (System.currentTimeMillis()-t1), this.getClass().getSimpleName());
    }

    /**
     * Helper method to retrieve multiple values by ids
     * @param collection
     * @param ids
     * @return
     */
    Collection<T> getValuesByIds(Map<String, T> collection, Set<String> ids) {
        Collection<T> result = new ArrayList<>();
        for (String id : ids) {
            T value = collection.get(id);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    /**
     * Returns values from provided Map where key starts with the provided datasetId
     * @param collection
     * @param datasetId
     * @return
     */
    Set<T> getValuesByDatasetId(Map<String, T> collection, String datasetId) {
        return collection.entrySet().stream().filter(e -> e.getKey().startsWith(datasetId + ":")).map(e -> e.getValue()).collect(Collectors.toSet());
    }

    Set<String> filterIdsByDataset(Set<String> idSet, List<String> excludedDatasetIds, String datasetId) {
        Set<String> requestedIds = new HashSet<>();
        if (excludedDatasetIds != null && !excludedDatasetIds.isEmpty()) {

            for (String id : idSet) {
                String datasetIdPrefix = id.substring(0, id.indexOf(":"));
                if (!excludedDatasetIds.contains(datasetIdPrefix)) {
                    requestedIds.add(id);
                }
            }

        } else if (datasetId != null && !datasetId.isEmpty()){

            String prefix = datasetId + ":";
            for (String id : idSet) {
                if (id.startsWith(prefix)) {
                    requestedIds.add(id);
                }
            }
        } else {
            requestedIds.addAll(idSet);
        }
        return requestedIds;
    }

    abstract void clearAllByDatasetId(String datasetId);


    /**
     * Compares object-equality by calculating and comparing MD5-checksum
     * @param existing
     * @param updated
     * @return
     */
    static boolean isEqual(Serializable existing, Serializable updated) {
        try {
            String checksumExisting = getChecksum(existing);
            String checksumUpdated = getChecksum(updated);

            return checksumExisting.equals(checksumUpdated);

        } catch (Throwable e) {
            //ignore - data will be updated
        }
        return false;
    }

    static String getChecksum(Serializable object) throws IOException, NoSuchAlgorithmException {
        ByteArrayOutputStream baos = null;
        ObjectOutputStream oos = null;
        try {
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(object);
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] thedigest = md.digest(baos.toByteArray());
            return DatatypeConverter.printHexBinary(thedigest);
        } finally {
            oos.close();
            baos.close();
        }
    }
}
