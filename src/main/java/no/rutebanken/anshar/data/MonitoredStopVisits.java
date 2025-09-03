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

import com.hazelcast.collection.ISet;
import com.hazelcast.map.IMap;
import com.hazelcast.query.Predicate;
import com.hazelcast.replicatedmap.ReplicatedMap;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.data.util.CustomStringUtils;
import no.rutebanken.anshar.data.util.SiriObjectStorageKeyUtil;
import no.rutebanken.anshar.routes.mapping.LineUpdaterService;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.mapping.VehicleJourneyService;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.util.StopMonitoringUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.siri.siri21.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Repository
public class MonitoredStopVisits extends SiriRepository<MonitoredStopVisit> {

    public static final String EMPTY_DESTINATION = "EmptyDestination";
    private static final Logger logger = LoggerFactory.getLogger(MonitoredStopVisits.class);
    private final Set<String> localSMDatasetList = new HashSet<>();
    @Autowired
    ExtendedHazelcastService hazelcastService;
    @Autowired
    StopPlaceUpdaterService stopPlaceUpdaterService;
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
    private SiriObjectFactory siriObjectFactory;
    @Autowired
    private SubscriptionConfig subscriptionConfig;
    @Autowired
    private VehicleJourneyService vehicleJourneyService;
    @Autowired
    private LineUpdaterService lineUpdaterService;

    protected MonitoredStopVisits() {
        super(SiriDataType.STOP_MONITORING);
    }


    @Override
    public Collection<MonitoredStopVisit> getAll() {

        ISet<String> datasetList = hazelcastService.getSharedSMDatasetList();
        List<MonitoredStopVisit> results = new ArrayList<>();

        for (String datasetId : datasetList) {
            results.addAll(hazelcastService.getMonitoredStopVisitsForDataset(datasetId).values());
        }
        return results;
    }

    public Set<SiriObjectStorageKey> getAllKeySets() {
        ISet<String> datasetList = hazelcastService.getSharedSMDatasetList();
        Set<SiriObjectStorageKey> results = new HashSet<>();

        for (String datasetId : datasetList) {
            results.addAll(hazelcastService.getMonitoredStopVisitsForDataset(datasetId).keySet());
        }
        return results;
    }

    @Override
    Map<SiriObjectStorageKey, MonitoredStopVisit> getAllAsMap() {
        ISet<String> datasetList = hazelcastService.getSharedSMDatasetList();
        Map<SiriObjectStorageKey, MonitoredStopVisit> results = new HashMap<>();

        for (String datasetId : datasetList) {
            results.putAll(hazelcastService.getMonitoredStopVisitsForDataset(datasetId));
        }
        return results;
    }

    @Override
    public int getSize() {
        return getAllKeySets().size();
    }

    public Map<String, Integer> getMonitoredDatasetSize() {
        Map<String, Integer> sizeMap = new HashMap<>();
        long t1 = System.currentTimeMillis();

        ISet<String> datasetList = hazelcastService.getSharedSMDatasetList();

        for (String datasetId : datasetList) {
            sizeMap.put(datasetId, (int) hazelcastService.getMonitoredStopVisitsForDataset(datasetId).values().stream().filter(s-> s.getMonitoredVehicleJourney().isMonitored()).count());
        }
        logger.debug("Calculating data-distribution (SM) took {} ms: {}", (System.currentTimeMillis() - t1), sizeMap);
        return sizeMap;
    }

    public Map<String, Integer> getNotMonitoredDatasetSize() {
        Map<String, Integer> sizeMap = new HashMap<>();
        long t1 = System.currentTimeMillis();

        ISet<String> datasetList = hazelcastService.getSharedSMDatasetList();

        for (String datasetId : datasetList) {
            sizeMap.put(datasetId, (int) hazelcastService.getMonitoredStopVisitsForDataset(datasetId).values()
                    .stream().filter(s-> !s.getMonitoredVehicleJourney().isMonitored() || s.getMonitoredVehicleJourney().isMonitored() == null).count());
        }
        logger.debug("Calculating data-distribution (SM) took {} ms: {}", (System.currentTimeMillis() - t1), sizeMap);
        return sizeMap;
    }

    @Override
    public void clearAllByDatasetId(String datasetId) {
        //Set<SiriObjectStorageKey> idsToRemove = monitoredStopVisits.keySet(createCodespacePredicate(datasetId));

        Set<SiriObjectStorageKey> keysToRemove = hazelcastService.getMonitoredStopVisitsForDataset(datasetId).keySet();
        logger.warn("Removing all data ({} ids) for {}", hazelcastService.getMonitoredStopVisitsForDataset(datasetId).size(), datasetId);
        hazelcastService.getMonitoredStopVisitsForDataset(datasetId).clear();


        for (SiriObjectStorageKey id : keysToRemove) {
            checksumCache.remove(id);
            idStartTimeMap.remove(id);
            idForPatternChanges.remove(id);
        }
        logger.warn("Removing all data done");

    }

    public void clearAll() {

        ISet<String> datasetList = hazelcastService.getSharedSMDatasetList();


        for (String datasetId : datasetList) {
            hazelcastService.getMonitoredStopVisitsForDataset(datasetId).clear();
        }
        logger.error("Deleting all data - should only be used in test!!!");
        checksumCache.clear();
        idStartTimeMap.clear();
        idForPatternChanges.clear();
        changesMap.clear();
        lastUpdateRequested.clear();
    }


    public Siri createServiceDelivery(String requestorRef, String datasetId, String clientTrackingName, int maxSize, Set<String> searchedStopIds, String messageId) {
        return createServiceDelivery(requestorRef, datasetId, clientTrackingName, null, maxSize, -1, searchedStopIds, messageId);
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, String clientTrackingName, List<String> excludedDatasetIds, int maxSize, long previewInterval, Set<String> searchedStopIds) {
        return createServiceDelivery(requestorId, datasetId, clientTrackingName, excludedDatasetIds, maxSize, previewInterval, searchedStopIds, null);
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, String clientTrackingName, List<String> excludedDatasetIds, int maxSize, long previewInterval, Set<String> searchedStopIds, String messageId) {

        if (requestorId == null) {
            requestorId = UUID.randomUUID().toString();
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

        Collection<MonitoredStopVisit> values = getMonitoredStopVisitsFromHazelcast(datasetId, sizeLimitedIds);

        logger.debug("Fetching data: {} ms", (System.currentTimeMillis() - t1));
        t1 = System.currentTimeMillis();

        Siri siri = siriObjectFactory.createSMServiceDelivery(values, requestorId, messageId);

        logger.debug("Creating SIRI-delivery: {} sm", (System.currentTimeMillis() - t1));

        siri.getServiceDelivery().setMoreData(isMoreData);

        return siri;
    }

    private Collection<MonitoredStopVisit> getMonitoredStopVisitsFromHazelcast(String datasetId, Set<SiriObjectStorageKey> sizeLimitedIds) {

        if (StringUtils.isNotEmpty(datasetId)) {
            return hazelcastService.getMonitoredStopVisitsForDataset(datasetId).getAll(sizeLimitedIds).values();
        } else {
            ISet<String> datasetList = hazelcastService.getSharedSMDatasetList();
            List<MonitoredStopVisit> results = new ArrayList<>();
            for (String dataset : datasetList) {
                Map<SiriObjectStorageKey, MonitoredStopVisit> datasetResults = hazelcastService.getMonitoredStopVisitsForDataset(dataset).getAll(sizeLimitedIds);
                if (datasetResults != null && datasetResults.size() > 0) {
                    results.addAll(datasetResults.values());
                }
            }
            return results;
        }
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
        if (StringUtils.isNotEmpty(datasetId)) {
            return new HashSet<>(hazelcastService.getMonitoredStopVisitsForDataset(datasetId).keySet(predicate));
        } else {
            ISet<String> datasetList = hazelcastService.getSharedSMDatasetList();
            Set<SiriObjectStorageKey> results = new HashSet<>();
            for (String dataset : datasetList) {
                Set<SiriObjectStorageKey> datasetResults = hazelcastService.getMonitoredStopVisitsForDataset(dataset).keySet(predicate);
                if (datasetResults != null && datasetResults.size() > 0) {
                    results.addAll(datasetResults);
                }
            }
            return results;

        }
    }


    @Override
    Collection<MonitoredStopVisit> getAllUpdates(String requestorId, String datasetId) {
        if (requestorId != null) {

            Set<SiriObjectStorageKey> idSet = changesMap.get(requestorId);
            lastUpdateRequested.set(requestorId, Instant.now(), configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);
            if (idSet != null) {
                Set<SiriObjectStorageKey> datasetFilteredIdSet = new HashSet<>();

                Collection<MonitoredStopVisit> changes = new HashSet<>();
                if (datasetId != null) {
                    idSet.stream().filter(key -> key.getCodespaceId().equals(datasetId)).forEach(datasetFilteredIdSet::add);
                    changes = hazelcastService.getMonitoredStopVisitsForDataset(datasetId).getAll(datasetFilteredIdSet).values();
                } else {
                    datasetFilteredIdSet.addAll(idSet);
                    ISet<String> datasetList = hazelcastService.getSharedSMDatasetList();
                    for (String datasetToRequest : datasetList) {
                        Map<SiriObjectStorageKey, MonitoredStopVisit> datasetResults = hazelcastService.getMonitoredStopVisitsForDataset(datasetToRequest).getAll(datasetFilteredIdSet);

                        if (datasetResults != null && datasetResults.size() > 0) {
                            changes.addAll(datasetResults.values());
                        }
                    }
                }


                Set<SiriObjectStorageKey> existingSet = changesMap.get(requestorId);
                if (existingSet == null) {
                    existingSet = new HashSet<>();
                }

                //Remove returned ids
                existingSet.removeAll(idSet);

                logger.debug("Returning {} changes to requestorRef {}", changes.size(), requestorId);
                return changes;
            } else {

                logger.debug("Returning all to requestorRef {}", requestorId);

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

        return getValuesByDatasetId(hazelcastService.getMonitoredStopVisitsForDataset(datasetId), datasetId);
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
            return ZonedDateTime.now().until(expiryTimestamp.plusMinutes(configuration.getSmGraceperiodMinutes()), ChronoUnit.MILLIS);
        }

        return -1;
    }


    @Override
    public Collection<MonitoredStopVisit> addAll(String datasetId, List<MonitoredStopVisit> smList) {
        Set<SiriObjectStorageKey> changes = new HashSet<>();
        Set<MonitoredStopVisit> addedData = new HashSet<>();

        Counter invalidLocationCounter = new CounterImpl(0);
        Counter notMeaningfulCounter = new CounterImpl(0);
        Counter outdatedCounter = new CounterImpl(0);
        Counter notUpdatedCounter = new CounterImpl(0);
        IMap<SiriObjectStorageKey, MonitoredStopVisit> currentHazelcastCache = hazelcastService.getMonitoredStopVisitsForDataset(datasetId);
        // Set<Long> deltaTimes = new HashSet<>();

        smList.stream()
                .filter(monitoredStopVisit -> monitoredStopVisit.getMonitoringRef() != null)
                .filter(monitoredStopVisit -> monitoredStopVisit.getRecordedAtTime() != null)
                .forEach(monitoredStopVisit -> {


//                    ZonedDateTime recordedAtTime = monitoredStopVisit.getRecordedAtTime();
//                    if (recordedAtTime != null) {
//                        deltaTimes.add(System.currentTimeMillis() - recordedAtTime.toInstant().toEpochMilli());
//                    }

                    String lineName = StopMonitoringUtils.getLineName(monitoredStopVisit).orElse(null);
                    String vehicleJourneyName = StopMonitoringUtils.getVehicleJourneyName(monitoredStopVisit).orElse(null);


                    String keyCriteria = monitoredStopVisit.getItemIdentifier() != null ? monitoredStopVisit.getItemIdentifier() : monitoredStopVisit.getRecordedAtTime().format(DateTimeFormatter.ISO_DATE);
                    SiriObjectStorageKey key = createKey(datasetId, keyCriteria, monitoredStopVisit.getMonitoringRef().getValue(), vehicleJourneyName, lineName);

                    if (!localSMDatasetList.contains(datasetId)) {
                        hazelcastService.getSharedSMDatasetList().add(datasetId);
                        localSMDatasetList.add(datasetId);
                    }

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
                    //if (existingChecksum != null && monitoredStopVisits.containsKey(key)) {
                    if (existingChecksum != null && currentHazelcastCache.containsKey(key)) {
                        //Exists - compare values

                        existing = currentHazelcastCache.get(key);

                        // if new visit's monitored status != old visit's status => update must be made
                        // else, a look on checksum is made
                        updated = existing.getMonitoredVehicleJourney().isMonitored() != monitoredStopVisit.getMonitoredVehicleJourney().isMonitored()
                                || !(currentChecksum.equals(existingChecksum));


                    } else {
                        //Does not exist
                        updated = true;
                    }
                    if (monitoredStopVisit.getMonitoredVehicleJourney().getDestinationRef() == null) {
                        DestinationRef destinationRef = new DestinationRef();
                        destinationRef.setValue(EMPTY_DESTINATION);
                        monitoredStopVisit.getMonitoredVehicleJourney().setDestinationRef(destinationRef);
                    }


                    if (CollectionUtils.isEmpty(monitoredStopVisit.getMonitoredVehicleJourney().getDestinationNames())) {
                        NaturalLanguageStringStructure destinationName = new NaturalLanguageStringStructure();
                        if (!EMPTY_DESTINATION.equals(monitoredStopVisit.getMonitoredVehicleJourney().getDestinationRef().getValue())) {
                            destinationName.setLang("FR");
                            String stopId = extractId(monitoredStopVisit.getMonitoredVehicleJourney().getDestinationRef().getValue());
                            destinationName.setValue(stopPlaceUpdaterService.getStopName(stopId, datasetId));
                        }
                        if (StringUtils.isEmpty(destinationName.getValue())) {
                            destinationName.setLang("EN");
                            destinationName.setValue(EMPTY_DESTINATION);
                        }
                        monitoredStopVisit.getMonitoredVehicleJourney().getDestinationNames().add(destinationName);
                    }


                    if (updated) {
                        checksumCache.put(key, currentChecksum, 5, TimeUnit.MINUTES); //Keeping all checksums for at least 5 minutes to avoid stale data

                        boolean keep = shouldKeepIncomingData(existing, monitoredStopVisit);

                        long expiration = getExpiration(monitoredStopVisit);


                        if (expiration > 0 && keep) {
                            feedFirstOrLastJourney(datasetId, monitoredStopVisit);
                            feedPublishedLineName(datasetId, monitoredStopVisit);
                            replaceSpecialCharacters(datasetId, monitoredStopVisit);
                            changes.add(key);
                            addedData.add(monitoredStopVisit);
                            currentHazelcastCache.set(key, monitoredStopVisit, expiration, TimeUnit.MILLISECONDS);
                            checksumCache.put(key, currentChecksum, expiration, TimeUnit.MILLISECONDS);

                        } else {
                            if (expiration < 0) {
                                registerNegativeExpiration(SiriDataType.STOP_MONITORING, datasetId);
                            }

                            outdatedCounter.increment();
                        }


                    } else {
                        notUpdatedCounter.increment();
                    }
                });

        //  recordDeltaTimes(SiriDataType.STOP_MONITORING, datasetId, deltaTimes);

        //  logger.debug("Updated {} (of {}) :: Ignored elements - Missing location:{}, Missing values: {}, Expired: {}, Not updated: {}", changes.size(), smList.size(), invalidLocationCounter.getValue(), notMeaningfulCounter.getValue(), outdatedCounter.getValue(), notUpdatedCounter.getValue());

        markDataReceived(SiriDataType.STOP_MONITORING, datasetId, smList.size(), changes.size(), outdatedCounter.getValue(), (invalidLocationCounter.getValue() + notMeaningfulCounter.getValue() + notUpdatedCounter.getValue()));
        return addedData;
    }

    private void feedPublishedLineName(String datasetId, MonitoredStopVisit monitoredStopVisit) {
        MonitoredVehicleJourneyStructure vehicleJourney = monitoredStopVisit.getMonitoredVehicleJourney();
        if (vehicleJourney.getPublishedLineNames() != null && vehicleJourney.getPublishedLineNames().size() > 0) {
            // published line name already filled. no need to add it
            return;
        }

        if (vehicleJourney.getLineRef() == null) {
            setEmptyPublishedLineName(vehicleJourney);
            return;
        }

        String lineId = vehicleJourney.getLineRef().getValue();
        Optional<IdProcessingParameters> idParamsOpt = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.LINE);
        if (idParamsOpt.isPresent()) {
            lineId = idParamsOpt.get().applyTransformationToString(lineId);
        }

        Optional<String> lineNameOpt = lineUpdaterService.getLineName(lineId);
        if (lineNameOpt.isPresent()) {
            NaturalLanguageStringStructure lineNameStruct = new NaturalLanguageStringStructure();
            lineNameStruct.setValue(lineNameOpt.get());
            vehicleJourney.getPublishedLineNames().add(lineNameStruct);
            return;
        }
        setEmptyPublishedLineName(vehicleJourney);
    }

    private void setEmptyPublishedLineName(MonitoredVehicleJourneyStructure vehicleJourney) {
        NaturalLanguageStringStructure lineNameStruct = new NaturalLanguageStringStructure();
        lineNameStruct.setValue("EMPTY_LINE");
        vehicleJourney.getPublishedLineNames().add(lineNameStruct);
    }

    private void feedFirstOrLastJourney(String datasetId, MonitoredStopVisit monitoredStopVisit) {

        if (monitoredStopVisit.getMonitoredVehicleJourney() == null) {
            MonitoredVehicleJourneyStructure monVJStructure = new MonitoredVehicleJourneyStructure();
            monVJStructure.setFirstOrLastJourney(FirstOrLastJourneyEnumeration.UNSPECIFIED);
            monitoredStopVisit.setMonitoredVehicleJourney(monVJStructure);
            return;
        }

        if (monitoredStopVisit.getMonitoredVehicleJourney().getFirstOrLastJourney() != null) {
            // keeping the incoming data, if existing
            return;
        }

        MonitoredVehicleJourneyStructure vehicleJourney = monitoredStopVisit.getMonitoredVehicleJourney();

        if (vehicleJourney.getFramedVehicleJourneyRef() == null || vehicleJourney.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef() == null) {
            // no vehicle journey id specified. Can't determine FirstOrLastJourney
            vehicleJourney.setFirstOrLastJourney(FirstOrLastJourneyEnumeration.UNSPECIFIED);
            return;
        }


        String vehicleJourneyRef = vehicleJourney.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef();
        Optional<IdProcessingParameters> idParamsOpt = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.VEHICLE_JOURNEY);
        if (idParamsOpt.isPresent()) {
            vehicleJourneyRef = idParamsOpt.get().applyTransformationToString(vehicleJourneyRef);
        }

        LocalDate transitDate = getDateFromMonitoredCall(vehicleJourney.getMonitoredCall());
        vehicleJourney.setFirstOrLastJourney(vehicleJourneyService.getServicePosition(transitDate, vehicleJourneyRef));
    }

    private LocalDate getDateFromMonitoredCall(MonitoredCallStructure monitoredCallStructure) {
        ZonedDateTime transitTime;

        if (monitoredCallStructure.getAimedDepartureTime() != null) {
            transitTime = monitoredCallStructure.getAimedDepartureTime();
        } else if (monitoredCallStructure.getExpectedDepartureTime() != null) {
            transitTime = monitoredCallStructure.getExpectedDepartureTime();
        } else if (monitoredCallStructure.getAimedArrivalTime() != null) {
            transitTime = monitoredCallStructure.getAimedArrivalTime();
        } else if (monitoredCallStructure.getExpectedArrivalTime() != null) {
            transitTime = monitoredCallStructure.getExpectedArrivalTime();
        } else {
            transitTime = ZonedDateTime.now();
        }
        return transitTime.toLocalDate();
    }

    private void replaceSpecialCharacters(String datasetId, MonitoredStopVisit monitoredStopVisit) {
        if (monitoredStopVisit.getMonitoredVehicleJourney() == null || monitoredStopVisit.getMonitoredVehicleJourney().getFramedVehicleJourneyRef() == null ||
                StringUtils.isEmpty(monitoredStopVisit.getMonitoredVehicleJourney().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef())) {
            return;
        }
        String vehicleJourneyRef = monitoredStopVisit.getMonitoredVehicleJourney().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef();

        Optional<IdProcessingParameters> idParamsOpt = subscriptionConfig.getIdParametersForDataset(datasetId, ObjectType.VEHICLE_JOURNEY);
        if (idParamsOpt.isPresent()) {
            vehicleJourneyRef = idParamsOpt.get().removeInputPrefixAndSuffix(vehicleJourneyRef);
        }

        monitoredStopVisit.getMonitoredVehicleJourney().getFramedVehicleJourneyRef().setDatedVehicleJourneyRef(CustomStringUtils.removeSpecialCharacters(vehicleJourneyRef));
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
            registerEmptyRecordedAtTime(SiriDataType.STOP_MONITORING);
            return false;
        }


        //new data only kept if more recent than old one
        if (newData.getRecordedAtTime().isAfter(oldData.getRecordedAtTime())) {
            return true;
        }
        registerNewRecordedAfterOld(SiriDataType.STOP_MONITORING);
        return false;
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
        return hazelcastService.getMonitoredStopVisitsForDataset(datasetId).get(createKey(datasetId, monitoredStopVisit.getItemIdentifier(), monitoringRef, vehicleJourney, line));
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
            Set<SiriObjectStorageKey> idSet = hazelcastService.getMonitoredStopVisitsForDataset(datasetId).keySet(predicate);
            results.put(datasetId, idSet.size());
        }
        return results;
    }

    public Map<String, Integer> getNbOfStopsByDataset(List<String> datasetIds) {
        Map<String, Integer> results = new HashMap<>();


        for (String datasetId : datasetIds) {
            Predicate<SiriObjectStorageKey, MonitoredStopVisit> predicate = SiriObjectStorageKeyUtil.getStopPredicate(null, datasetId, null);
            Set<SiriObjectStorageKey> idSet = hazelcastService.getMonitoredStopVisitsForDataset(datasetId).keySet(predicate);
            Set<String> stopSet = new HashSet();
            for (SiriObjectStorageKey siriObjectStorageKey : idSet) {

                stopSet.add(siriObjectStorageKey.getStopRef());
            }
            results.put(datasetId, stopSet.size());
        }
        return results;
    }

    public Set<String> getAllDatasetIds() {
        return hazelcastService.getSharedSMDatasetList();
    }

    public void cancelStopVsits(String datasetId, List<MonitoredStopVisitCancellation> incomingMonitoredStopVisitsCancellations) {
        for (MonitoredStopVisitCancellation incomingMonitoredStopVisitsCancellation : incomingMonitoredStopVisitsCancellations) {
            String lineName = StopMonitoringUtils.getLineName(incomingMonitoredStopVisitsCancellation).orElse(null);
            String vehicleJourneyName = StopMonitoringUtils.getVehicleJourneyName(incomingMonitoredStopVisitsCancellation).orElse(null);
            String keyCriteria = incomingMonitoredStopVisitsCancellation.getItemRef() != null ? incomingMonitoredStopVisitsCancellation.getItemRef().getValue() : incomingMonitoredStopVisitsCancellation.getRecordedAtTime().format(DateTimeFormatter.ISO_DATE);
            SiriObjectStorageKey key = createKey(datasetId, keyCriteria, incomingMonitoredStopVisitsCancellation.getMonitoringRef().getValue(), vehicleJourneyName, lineName);
            hazelcastService.getMonitoredStopVisitsForDataset(datasetId).delete(key);
            //logger.debug("SM - key deleted:" + key);
        }
    }

    /**
     * Extract stopCode from a raw Id with ":" separators
     *
     * @param rawId the raw id with : separators (e.g: SIRI_NVP_037:StopPoint:BP:MADU01:LOC)
     * @return the stop code
     */
    private String extractId(String rawId) {
        if (rawId.contains(":")) {
            String idWithoutLoc = Strings.CS.removeEnd(rawId, ":LOC");
            String[] idTab = idWithoutLoc.split(":");
            return idTab[idTab.length - 1];
        } else {
            return rawId;
        }
    }

}
