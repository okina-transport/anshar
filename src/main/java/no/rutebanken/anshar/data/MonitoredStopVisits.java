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

import com.hazelcast.map.IMap;
import com.hazelcast.replicatedmap.ReplicatedMap;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.siri.siri20.AbstractItemStructure;
import uk.org.siri.siri20.MonitoredCallStructure;
import uk.org.siri.siri20.MonitoredStopVisit;
import uk.org.siri.siri20.MonitoredVehicleJourneyStructure;
import uk.org.siri.siri20.Siri;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

@Repository
// TODO MHI
public class MonitoredStopVisits extends SiriRepository<MonitoredStopVisit> {

    private final Logger logger = LoggerFactory.getLogger(MonitoredStopVisits.class);

    @Autowired
    private IMap<SiriObjectStorageKey, MonitoredStopVisit> monitoredStopVisits;

    @Autowired
    @Qualifier("getSmChecksumMap")
    private ReplicatedMap<SiriObjectStorageKey, String> checksumCache;

    @Autowired
    @Qualifier("getMonitoredStopVisitChangesMap")
    private IMap<String, Set<SiriObjectStorageKey>> changesMap;

    @Autowired
    @Qualifier("getLastSmUpdateRequest")
    private IMap<String, Instant> lastUpdateRequested;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    ExtendedHazelcastService hazelcastService;

    @PostConstruct
    private void initializeUpdateCommitter() {
        super.initBufferCommitter(hazelcastService, lastUpdateRequested, changesMap, configuration.getChangeBufferCommitFrequency());
    }

    @Override
    Collection<MonitoredStopVisit> getAll() {
        return monitoredStopVisits.values();
    }

    @Override
    int getSize() {
        return monitoredStopVisits.keySet().size();
    }

    public Map<String, Integer> getDatasetSize() {
        Map<String, Integer> sizeMap = new HashMap<>();
        long t1 = System.currentTimeMillis();
        monitoredStopVisits.keySet().forEach(key -> {
            String datasetId = key.getCodespaceId();

            Integer count = sizeMap.getOrDefault(datasetId, 0);
            sizeMap.put(datasetId, count + 1);
        });
        logger.debug("Calculating data-distribution (SM) took {} ms: {}", (System.currentTimeMillis() - t1), sizeMap);
        return sizeMap;
    }

    public Map<String, Integer> getLocalDatasetSize() {
        Map<String, Integer> sizeMap = new HashMap<>();
        long t1 = System.currentTimeMillis();
        monitoredStopVisits.localKeySet().forEach(key -> {
            String datasetId = key.getCodespaceId();

            Integer count = sizeMap.getOrDefault(datasetId, 0);
            sizeMap.put(datasetId, count + 1);
        });
        logger.debug("Calculating data-distribution (SM) took {} ms: {}", (System.currentTimeMillis() - t1), sizeMap);
        return sizeMap;
    }

    public Integer getDatasetSize(String datasetId) {
        return Math.toIntExact(monitoredStopVisits.keySet().stream()
                .filter(key -> datasetId.equals(key.getCodespaceId()))
                .count());
    }


    @Override
    void clearAllByDatasetId(String datasetId) {
        Set<SiriObjectStorageKey> idsToRemove = monitoredStopVisits.keySet(createCodespacePredicate(datasetId));

        logger.warn("Removing all data ({} ids) for {}", idsToRemove.size(), datasetId);

        for (SiriObjectStorageKey id : idsToRemove) {
            monitoredStopVisits.delete(id);
            checksumCache.remove(id);
        }
    }

    public void clearAll() {
        logger.error("Deleting all data - should only be used in test!!!");
        monitoredStopVisits.clear();
    }

    /**
     * @return All Stop monitoring that are still valid
     */
    @Override
    public Collection<MonitoredStopVisit> getAll(String datasetId) {
        if (datasetId == null) {
            return getAll();
        }

        return getValuesByDatasetId(monitoredStopVisits, datasetId);
    }


    @Override
    Collection<MonitoredStopVisit> getAllUpdates(String requestorId, String datasetId) {
        if (requestorId != null) {

            Set<SiriObjectStorageKey> idSet = changesMap.get(requestorId);
            lastUpdateRequested.set(requestorId, Instant.now(), configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);
            if (idSet != null) {
                Set<SiriObjectStorageKey> datasetFilteredIdSet = new HashSet<>();

                if (datasetId != null) {
                    idSet.stream().filter(key -> key.getCodespaceId().equals(datasetId)).forEach(datasetFilteredIdSet::add);
                } else {
                    datasetFilteredIdSet.addAll(idSet);
                }

                Collection<MonitoredStopVisit> changes = monitoredStopVisits.getAll(datasetFilteredIdSet).values();

                Set<SiriObjectStorageKey> existingSet = changesMap.get(requestorId);
                if (existingSet == null) {
                    existingSet = new HashSet<>();
                }

                //Remove returned ids
                existingSet.removeAll(idSet);

                if (idSet.size() > monitoredStopVisits.size()) {
                    //Remove outdated ids
                    existingSet.removeIf(id -> !monitoredStopVisits.containsKey(id));
                }

                updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, existingSet, configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);

                logger.info("Returning {} changes to requestorRef {}", changes.size(), requestorId);
                return changes;
            } else {

                logger.info("Returning all to requestorRef {}", requestorId);
                updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, new HashSet<>(), configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);

            }
        }

        return getAll(datasetId);
    }

    public Siri createServiceDelivery(final String lineRef) {
        SortedSet<MonitoredStopVisit> monitoredStopVisitsStructures = new TreeSet<>(Comparator.comparing(AbstractItemStructure::getRecordedAtTime));

        final Set<SiriObjectStorageKey> lineRefKeys = monitoredStopVisits.keySet(createLineRefPredicate(lineRef));

        monitoredStopVisitsStructures.addAll(monitoredStopVisits.getAll(lineRefKeys).values());

        return siriObjectFactory.createSMServiceDelivery(monitoredStopVisitsStructures);
    }

    // TODO MHI : copié / collé, à revoir
    @Override
    Collection<MonitoredStopVisit> addAll(String datasetId, List<MonitoredStopVisit> smList) {
        Set<SiriObjectStorageKey> changes = new HashSet<>();
        Set<MonitoredStopVisit> addedData = new HashSet<>();

        Counter invalidLocationCounter = new CounterImpl(0);
        Counter notMeaningfulCounter = new CounterImpl(0);
        Counter outdatedCounter = new CounterImpl(0);
        Counter notUpdatedCounter = new CounterImpl(0);

        smList.stream()
                .filter(monitoredStopVisit -> monitoredStopVisit.getMonitoringRef() != null)
                .filter(monitoredStopVisit -> monitoredStopVisit.getItemIdentifier() != null)
                .forEach(monitoredStopVisit -> {

                    SiriObjectStorageKey key = createKey(datasetId, monitoredStopVisit.getItemIdentifier());

                    String currentChecksum = null;
                    ZonedDateTime validUntilTime = monitoredStopVisit.getValidUntilTime();
                    try {
                        // Calculate checksum without "ValidUntilTime" - thus ignoring "fake" updates where only validity is updated
                        monitoredStopVisit.setValidUntilTime(null);
                        currentChecksum = getChecksum(monitoredStopVisit);
                    } catch (Exception e) {
                        //Ignore - data will be updated
                    } finally {
                        //Set original ValidUntilTime back
                        monitoredStopVisit.setValidUntilTime(validUntilTime);
                    }

                    String existingChecksum = checksumCache.get(key);

                    boolean updated;
                    if (existingChecksum != null && monitoredStopVisits.containsKey(key)) {
                        //Exists - compare values
                        updated =  !(currentChecksum.equals(existingChecksum));
                    } else {
                        //Does not exist
                        updated = true;
                    }

                    if (updated) {
                        checksumCache.put(key, currentChecksum, 5, TimeUnit.MINUTES); //Keeping all checksums for at least 5 minutes to avoid stale data

                        MonitoredStopVisit existing = monitoredStopVisits.get(key);

                        boolean keep = (existing == null); //No existing data i.e. keep

                        if (existing != null &&
                                (monitoredStopVisit.getRecordedAtTime() != null && existing.getRecordedAtTime() != null)) {
                            //Newer data has already been processed
                            keep = monitoredStopVisit.getRecordedAtTime().isAfter(existing.getRecordedAtTime());
                        }

                        long expiration = getExpiration(monitoredStopVisit);

                        if (expiration > 0 && keep) {
                            changes.add(key);
                            addedData.add(monitoredStopVisit);
                            monitoredStopVisits.set(key, monitoredStopVisit, expiration, TimeUnit.MILLISECONDS);
                            checksumCache.put(key, currentChecksum, expiration, TimeUnit.MILLISECONDS);

                            // TODO MHI : push somewhere ?
//                            siriSmMqttHandler.pushToMqttAsync(datasetId, monitoredStopVisit);

                        } else {
                            outdatedCounter.increment();
                        }


                    } else {
                        notUpdatedCounter.increment();
                    }

                });

        logger.info("Updated {} (of {}) :: Ignored elements - Missing location:{}, Missing values: {}, Skipped: {}, Not updated: {}", changes.size(), smList.size(), invalidLocationCounter.getValue(), notMeaningfulCounter.getValue(), outdatedCounter.getValue(), notUpdatedCounter.getValue());

        markDataReceived(SiriDataType.STOP_MONITORING, datasetId, smList.size(), changes.size(), outdatedCounter.getValue(), (invalidLocationCounter.getValue() + notMeaningfulCounter.getValue() + notUpdatedCounter.getValue()));

        markIdsAsUpdated(changes);

        return addedData;
    }

    @Override
    MonitoredStopVisit add(String datasetId, MonitoredStopVisit monitoredStopVisit) {
        if (monitoredStopVisit == null || monitoredStopVisit.getMonitoringRef() == null) {
            return null;
        }
        List<MonitoredStopVisit> monitoredStopVisits = new ArrayList<>();
        monitoredStopVisits.add(monitoredStopVisit);
        addAll(datasetId, monitoredStopVisits);
        return this.monitoredStopVisits.get(createKey(datasetId, monitoredStopVisit.getItemIdentifier()));
    }

    @Override
    long getExpiration(MonitoredStopVisit monitoredStopVisit) {
        MonitoredVehicleJourneyStructure monitoredVehicleJourney = monitoredStopVisit.getMonitoredVehicleJourney();

        ZonedDateTime expiryTimestamp = null;
        if (monitoredVehicleJourney.getMonitoredCall() != null) {
            MonitoredCallStructure estimatedCalls = monitoredVehicleJourney.getMonitoredCall();

            if (estimatedCalls.getAimedArrivalTime() != null) {
                expiryTimestamp = estimatedCalls.getAimedArrivalTime();
            }
            if (estimatedCalls.getAimedDepartureTime() != null) {
                expiryTimestamp = estimatedCalls.getAimedDepartureTime();
            }
            if (estimatedCalls.getExpectedArrivalTime() != null) {
                expiryTimestamp = estimatedCalls.getExpectedArrivalTime();
            }
            if (estimatedCalls.getExpectedDepartureTime() != null) {
                expiryTimestamp = estimatedCalls.getExpectedDepartureTime();
            }
        }

        if (expiryTimestamp != null) {
            return ZonedDateTime.now().until(expiryTimestamp.plus(configuration.getSmGraceperiodMinutes(), ChronoUnit.MINUTES), ChronoUnit.MILLIS);
        }

        return -1;
    }

    /**
     * Creates unique key - assumes that any operator has a set of unique StopMonitoringRefs
     * @param datasetId
     * @param monitoredStopVisitIdentifier
     * @return
     */
    private SiriObjectStorageKey createKey(String datasetId, String monitoredStopVisitIdentifier) {
        return new SiriObjectStorageKey(datasetId, null, monitoredStopVisitIdentifier);
    }


}
