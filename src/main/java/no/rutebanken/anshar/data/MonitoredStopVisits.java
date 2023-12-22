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
import com.hazelcast.query.Predicate;
import com.hazelcast.replicatedmap.ReplicatedMap;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.data.util.SiriObjectStorageKeyUtil;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.util.StopMonitoringUtils;
import org.apache.commons.lang3.StringUtils;
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.siri.siri20.*;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Repository
// TODO MHI
public class MonitoredStopVisits extends SiriRepository<MonitoredStopVisit> {

    private static final Logger logger = LoggerFactory.getLogger(MonitoredStopVisits.class);

    @Autowired
    @Qualifier("getMonitoredStopVisits")
    private IMap<SiriObjectStorageKey, MonitoredStopVisit> monitoredStopVisits;

    @Autowired
    @Qualifier("getSmChecksumMap")
    private ReplicatedMap<SiriObjectStorageKey, String> checksumCache;

    @Autowired
    @Qualifier("getIdForPatternChangesMap")
    private IMap<SiriObjectStorageKey, String> idForPatternChanges;

    @Autowired
    @Qualifier("getIdStartTimeMap")
    private IMap<SiriObjectStorageKey, ZonedDateTime> idStartTimeMap;

    @Autowired
    @Qualifier("getMonitoredStopVisitChangesMap")
    private IMap<String, Set<SiriObjectStorageKey>> changesMap;

    @Autowired
    @Qualifier("getLastSmUpdateRequest")
    private IMap<String, Instant> lastUpdateRequested;

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private RequestorRefRepository requestorRefRepository;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    ExtendedHazelcastService hazelcastService;

    protected MonitoredStopVisits() {
        super(SiriDataType.STOP_MONITORING);
    }

    @PostConstruct
    private void initializeUpdateCommitter() {
        super.initBufferCommitter(hazelcastService, lastUpdateRequested, changesMap, configuration.getChangeBufferCommitFrequency());
    }

    @Override
    public Collection<MonitoredStopVisit> getAll() {
        return monitoredStopVisits.values();
    }

    @Override
    Map<SiriObjectStorageKey, MonitoredStopVisit> getAllAsMap() {
        return monitoredStopVisits;
    }

    @Override
    public int getSize() {
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
            idStartTimeMap.remove(id);
            idForPatternChanges.remove(id);
        }
        logger.warn("Removing all data done");

    }

    public void clearAll() {
        logger.error("Deleting all data - should only be used in test!!!");
        monitoredStopVisits.clear();
        checksumCache.clear();
        idStartTimeMap.clear();
        idForPatternChanges.clear();
        changesMap.clear();
        lastUpdateRequested.clear();
    }


    public Siri createServiceDelivery(String requestorRef, String datasetId, String clientTrackingName, int maxSize, Set<String> searchedStopIds) {
        return createServiceDelivery(requestorRef, datasetId, clientTrackingName, null, maxSize, -1, searchedStopIds);
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, String clientTrackingName, List<String> excludedDatasetIds, int maxSize, long previewInterval, Set<String> searchedStopIds) {

        if (StringUtils.isNotEmpty(datasetId)) {
            requestorRefRepository.touchRequestorRef(requestorId, datasetId, clientTrackingName, SiriDataType.STOP_MONITORING);
        } else {
            requestorRefRepository.touchRequestorRef(requestorId, null, clientTrackingName, SiriDataType.STOP_MONITORING);
        }


        int trackingPeriodMinutes = configuration.getTrackingPeriodMinutes();

        boolean isAdHocRequest = false;

        if (requestorId == null) {
            requestorId = UUID.randomUUID().toString();
            trackingPeriodMinutes = configuration.getAdHocTrackingPeriodMinutes();
            isAdHocRequest = true;
        }

        // Filter by (datasetId and/or searchedStopIds) OR (excludedDatasetIds and/or searchedStopIds)
        Set<SiriObjectStorageKey> requestedIds = new HashSet<>();
        if (StringUtils.isNotEmpty(datasetId)) {
            requestedIds.addAll(generateIdSet(datasetId, searchedStopIds, excludedDatasetIds));
        } else {
            requestedIds.addAll(generateIdSet(null, searchedStopIds, excludedDatasetIds));
        }


        final ZonedDateTime previewExpiry = ZonedDateTime.now().plusSeconds(previewInterval / 1000);

        Set<SiriObjectStorageKey> startTimes = new HashSet<>();

        // TODO MHI
        if (previewInterval >= 0) {
            long t1 = System.currentTimeMillis();
            startTimes.addAll(idStartTimeMap
                    .entrySet().stream()
                    .filter(entry -> entry.getValue().isBefore(previewExpiry))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet()));

            logger.info("Found {} ids starting within {} ms in {} ms", startTimes.size(), previewInterval, (System.currentTimeMillis() - t1));
        }

        final AtomicInteger previewIntervalInclusionCounter = new AtomicInteger();
        final AtomicInteger previewIntervalExclusionCounter = new AtomicInteger();
        java.util.function.Predicate<SiriObjectStorageKey> previewIntervalFilter = id -> {

            if (idForPatternChanges.containsKey(id) || startTimes.contains(id)) {
                // Is valid in requested previewInterval
                previewIntervalInclusionCounter.incrementAndGet();
                return true;
            }

            previewIntervalExclusionCounter.incrementAndGet();
            return false;
        };

        long t1 = System.currentTimeMillis();
        // TODO MHI
        Set<SiriObjectStorageKey> sizeLimitedIds = requestedIds
                .stream()
                .filter(id -> previewInterval < 0 || previewIntervalFilter.test(id))
                .limit(maxSize)
                .collect(Collectors.toSet());

        long t2 = System.currentTimeMillis();

        //Remove collected objects
        sizeLimitedIds.forEach(requestedIds::remove);

        long t3 = System.currentTimeMillis();

        logger.debug("Filter by startTime: {}, limiting size: {} ms", (t2 - t1), (t3 - t2));

        t1 = System.currentTimeMillis();

        Boolean isMoreData = (previewIntervalExclusionCounter.get() + sizeLimitedIds.size()) < requestedIds.size();

        Collection<MonitoredStopVisit> values = monitoredStopVisits.getAll(sizeLimitedIds).values();
        logger.debug("Fetching data: {} ms", (System.currentTimeMillis() - t1));
        t1 = System.currentTimeMillis();

        Siri siri = siriObjectFactory.createSMServiceDelivery(values);
        logger.debug("Creating SIRI-delivery: {} sm", (System.currentTimeMillis() - t1));

        siri.getServiceDelivery().setMoreData(isMoreData);

        if (!isAdHocRequest) {

            MessageRefStructure msgRef = new MessageRefStructure();
            msgRef.setValue(requestorId);
            siri.getServiceDelivery().setRequestMessageRef(msgRef);

            if (requestedIds.size() > monitoredStopVisits.size()) {
                //Remove outdated ids
                requestedIds.removeIf(id -> !monitoredStopVisits.containsKey(id));
            }

            //Update change-tracker
            // updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, requestedIds, trackingPeriodMinutes, TimeUnit.MINUTES);

        }

        return siri;
    }

    /**
     * Generates a set of keys that matches with user's request
     *
     * @param datasetId          dataset id
     * @param searchedStopRefs   stop place ids filters
     * @param excludedDatasetIds dataset ids excluded
     * @return a set of keys matching with filters
     */
    private Set<SiriObjectStorageKey> generateIdSet(String datasetId, Set<String> searchedStopRefs, List<String> excludedDatasetIds) {
        // Get all relevant ids
        Predicate<SiriObjectStorageKey, MonitoredStopVisit> predicate = SiriObjectStorageKeyUtil.getStopPredicate(searchedStopRefs, datasetId, excludedDatasetIds);
        return new HashSet<>(monitoredStopVisits.keySet(predicate));
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

                logger.debug("Returning {} changes to requestorRef {}", changes.size(), requestorId);
                return changes;
            } else {

                logger.debug("Returning all to requestorRef {}", requestorId);
                updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, new HashSet<>(), configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);

            }
        }

        return getAll(datasetId);
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

    // TODO MHI : copié / collé, à revoir
    @Override
    public Collection<MonitoredStopVisit> addAll(String datasetId, List<MonitoredStopVisit> smList) {
        Set<SiriObjectStorageKey> changes = new HashSet<>();
        Set<MonitoredStopVisit> addedData = new HashSet<>();

        Counter invalidLocationCounter = new CounterImpl(0);
        Counter notMeaningfulCounter = new CounterImpl(0);
        Counter outdatedCounter = new CounterImpl(0);
        Counter notUpdatedCounter = new CounterImpl(0);

        smList.stream()
                .filter(monitoredStopVisit -> monitoredStopVisit.getMonitoringRef() != null)
                .filter(monitoredStopVisit -> monitoredStopVisit.getRecordedAtTime() != null)
                .forEach(monitoredStopVisit -> {

                    String lineName = StopMonitoringUtils.getLineName(monitoredStopVisit).orElse(null);
                    String vehicleJourneyName = StopMonitoringUtils.getVehicleJourneyName(monitoredStopVisit).orElse(null);

                    String keyCriteria = monitoredStopVisit.getItemIdentifier() != null ? monitoredStopVisit.getItemIdentifier() : monitoredStopVisit.getRecordedAtTime().format(DateTimeFormatter.ISO_DATE);
                    SiriObjectStorageKey key = createKey(datasetId, keyCriteria, monitoredStopVisit.getMonitoringRef().getValue(), vehicleJourneyName, lineName);

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
                    MonitoredStopVisit existing = null;
                    if (existingChecksum != null && monitoredStopVisits.containsKey(key)) {
                        //Exists - compare values

                        existing = monitoredStopVisits.get(key);

                        // if new visit's monitored status != old visit's status => update must be made
                        // else, a look on checksum is made
                        updated = existing.getMonitoredVehicleJourney().isMonitored() != monitoredStopVisit.getMonitoredVehicleJourney().isMonitored()
                                || !(currentChecksum.equals(existingChecksum));
                    } else {
                        //Does not exist
                        updated = true;
                    }

                    if (monitoredStopVisit.getMonitoredVehicleJourney() != null && monitoredStopVisit.getMonitoredVehicleJourney().getPublishedLineNames().size() == 1) {
                        NaturalLanguageStringStructure emptyPubLine = new NaturalLanguageStringStructure();
                        emptyPubLine.setLang("FR");
                        emptyPubLine.setValue(" ");
                        monitoredStopVisit.getMonitoredVehicleJourney().getPublishedLineNames().add(emptyPubLine);
                    }

                    if (monitoredStopVisit.getMonitoredVehicleJourney().getJourneyPatternRef() == null) {
                        JourneyPatternRef journeyPatternRef = new JourneyPatternRef();
                        String journeyPatternRefStr = monitoredStopVisit.getMonitoredVehicleJourney().getLineRef() != null ? monitoredStopVisit.getMonitoredVehicleJourney().getLineRef().getValue() : "EmptyJourneyPatternRef";
                        journeyPatternRef.setValue(journeyPatternRefStr);
                        monitoredStopVisit.getMonitoredVehicleJourney().setJourneyPatternRef(journeyPatternRef);
                    }

                    if (monitoredStopVisit.getMonitoredVehicleJourney().getDestinationRef() == null) {
                        DestinationRef destinationRef = new DestinationRef();
                        destinationRef.setValue("EmptyDestination");
                        monitoredStopVisit.getMonitoredVehicleJourney().setDestinationRef(destinationRef);
                    }

                    if (monitoredStopVisit.getMonitoredVehicleJourney().getDestinationNames().isEmpty()) {
                        NaturalLanguageStringStructure emptyDestinationName = new NaturalLanguageStringStructure();
                        emptyDestinationName.setLang("EN");
                        emptyDestinationName.setValue("EmptyDestination");
                        monitoredStopVisit.getMonitoredVehicleJourney().getDestinationNames().add(emptyDestinationName);
                    }


                    if (updated) {
                        checksumCache.put(key, currentChecksum, 5, TimeUnit.MINUTES); //Keeping all checksums for at least 5 minutes to avoid stale data


                        boolean keep = shouldKeepIncomingData(existing, monitoredStopVisit);

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

        logger.debug("Updated {} (of {}) :: Ignored elements - Missing location:{}, Missing values: {}, Expired: {}, Not updated: {}", changes.size(), smList.size(), invalidLocationCounter.getValue(), notMeaningfulCounter.getValue(), outdatedCounter.getValue(), notUpdatedCounter.getValue());

        markDataReceived(SiriDataType.STOP_MONITORING, datasetId, smList.size(), changes.size(), outdatedCounter.getValue(), (invalidLocationCounter.getValue() + notMeaningfulCounter.getValue() + notUpdatedCounter.getValue()));

        markIdsAsUpdated(changes);

        return addedData;
    }

    /**
     * Determine if incoming data (new data) must be kept or not
     *
     * @return true : incoming data must be kept. It must replace the old data
     * false : incoming data should be ignored
     */
    private boolean shouldKeepIncomingData(MonitoredStopVisit oldData, MonitoredStopVisit newData) {
        if (oldData == null) {
            //No existing data => new data must be kept
            return true;
        }

        if (oldData.getMonitoredVehicleJourney().isMonitored() != newData.getMonitoredVehicleJourney().isMonitored()) {
            //monitored status has changed => new data must be kept
            return true;
        }

        if (newData.getRecordedAtTime() == null || oldData.getRecordedAtTime() == null) {
            // Inconsistent data => new data ignored
            return false;
        }

        //new data only kept if more recent than old one
        return newData.getRecordedAtTime().isAfter(oldData.getRecordedAtTime());
    }


    @Override
    public MonitoredStopVisit add(String datasetId, MonitoredStopVisit monitoredStopVisit) {
        return add(datasetId, monitoredStopVisit, null, null);
    }

    MonitoredStopVisit add(String datasetId, MonitoredStopVisit monitoredStopVisit, String vehicleJourney, String line) {
        if (monitoredStopVisit == null || monitoredStopVisit.getMonitoringRef() == null) {
            return null;
        }
        List<MonitoredStopVisit> monitoredStopVisits = new ArrayList<>();
        monitoredStopVisits.add(monitoredStopVisit);
        addAll(datasetId, monitoredStopVisits);

        String monitoringRef = monitoredStopVisit.getMonitoringRef() == null ? null : monitoredStopVisit.getMonitoringRef().getValue();
        return this.monitoredStopVisits.get(createKey(datasetId, monitoredStopVisit.getItemIdentifier(), monitoringRef, vehicleJourney, line));
    }


    /**
     * Creates unique key - assumes that any operator has a set of unique StopMonitoringRefs
     *
     * @param datasetId
     * @param monitoredStopVisitIdentifier
     * @return
     */
    private SiriObjectStorageKey createKey(String datasetId, String monitoredStopVisitIdentifier, String monitoringRef, String vehicleJourney, String line) {
        return new SiriObjectStorageKey(datasetId, line, monitoredStopVisitIdentifier, monitoringRef, vehicleJourney, null);
    }

    public void writeStatistics(List<String> datasetIds) {
        Map<String, Integer> results = getNbOfItemsByDataset(datasetIds);
        for (Map.Entry<String, Integer> entry : results.entrySet()) {
            logger.info("Okina-StopMonitoring " + entry.getKey() + " : " + entry.getValue() + " MonitoredRefs");
        }
    }


    public Map<String, Integer> getNbOfItemsByDataset(List<String> datasetIds) {
        Map<String, Integer> results = new HashMap<>();

        for (String datasetId : datasetIds) {

            Predicate<SiriObjectStorageKey, MonitoredStopVisit> predicate = SiriObjectStorageKeyUtil.getStopPredicate(null, datasetId, null);
            Set<SiriObjectStorageKey> idSet = monitoredStopVisits.keySet(predicate);
            results.put(datasetId, idSet.size());
        }
        return results;
    }

    public Map<String, Integer> getNbOfStopsByDataset(List<String> datasetIds) {
        Map<String, Integer> results = new HashMap<>();


        for (String datasetId : datasetIds) {
            Predicate<SiriObjectStorageKey, MonitoredStopVisit> predicate = SiriObjectStorageKeyUtil.getStopPredicate(null, datasetId, null);
            Set<SiriObjectStorageKey> idSet = monitoredStopVisits.keySet(predicate);
            Set<String> stopSet = new HashSet();
            for (SiriObjectStorageKey siriObjectStorageKey : idSet) {

                if (!stopSet.contains(siriObjectStorageKey.getStopRef())) {
                    stopSet.add(siriObjectStorageKey.getStopRef());
                }
            }
            results.put(datasetId, stopSet.size());
        }
        return results;
    }

    public Set<String> getAllDatasetIds() {
        return monitoredStopVisits.keySet().stream().map(SiriObjectStorageKey::getCodespaceId).collect(Collectors.toSet());
    }

    public void cancelStopVsits(String datasetId, List<MonitoredStopVisitCancellation> incomingMonitoredStopVisitsCancellations) {
        for (MonitoredStopVisitCancellation incomingMonitoredStopVisitsCancellation : incomingMonitoredStopVisitsCancellations) {
            String lineName = StopMonitoringUtils.getLineName(incomingMonitoredStopVisitsCancellation).orElse(null);
            String vehicleJourneyName = StopMonitoringUtils.getVehicleJourneyName(incomingMonitoredStopVisitsCancellation).orElse(null);
            String keyCriteria = incomingMonitoredStopVisitsCancellation.getItemRef() != null ? incomingMonitoredStopVisitsCancellation.getItemRef().getValue() : incomingMonitoredStopVisitsCancellation.getRecordedAtTime().format(DateTimeFormatter.ISO_DATE);
            SiriObjectStorageKey key = createKey(datasetId, keyCriteria, incomingMonitoredStopVisitsCancellation.getMonitoringRef().getValue(), vehicleJourneyName, lineName);
            monitoredStopVisits.delete(key);
            logger.debug("SM - key deleted:" + key);
        }
    }
}
