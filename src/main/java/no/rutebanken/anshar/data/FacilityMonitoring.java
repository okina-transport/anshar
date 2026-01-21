package no.rutebanken.anshar.data;

import com.hazelcast.map.IMap;
import com.hazelcast.query.Predicate;
import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.util.SiriObjectStorageKeyUtil;
import no.rutebanken.anshar.routes.health.InputSubscriptionData;
import no.rutebanken.anshar.routes.kafka.KafkaConfig;
import no.rutebanken.anshar.routes.kafka.KafkaRouteBuilder;
import no.rutebanken.anshar.routes.mapping.ParkingIdsService;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.Strings;
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.siri.siri21.FacilityConditionStructure;
import uk.org.siri.siri21.FacilityLocationStructure;
import uk.org.siri.siri21.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri21.Siri;

import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class FacilityMonitoring extends SiriRepository<FacilityConditionStructure> {

    private final IMap<SiriObjectStorageKey, FacilityConditionStructure> facilityMonitoring;
    private final IMap<String, Set<SiriObjectStorageKey>> changesMap;
    private final IMap<String, Instant> lastUpdateRequested;
    private final IMap<SiriObjectStorageKey, String> checksumCache;
    private final AnsharConfiguration configuration;
    private final SiriObjectFactory siriObjectFactory;
    private final ParkingIdsService parkingIdsService;
    private final KafkaConfig kafkaConfig;
    @Produce(KafkaRouteBuilder.SEND_TR_IN_SUBSCRIPTION_DATA_TO_KAFKA)
    private ProducerTemplate sendTrInSubscriptionDataToKafka;


    protected FacilityMonitoring(@Qualifier("getFacilityMonitoring") IMap<SiriObjectStorageKey, FacilityConditionStructure> facilityMonitoring,
                                 @Qualifier("getFacilityMonitoringChangesMap") IMap<String, Set<SiriObjectStorageKey>> changesMap,
                                 @Qualifier("getLastFmUpdateRequest") IMap<String, Instant> lastUpdateRequested,
                                 @Qualifier("getFmChecksumMap") IMap<SiriObjectStorageKey, String> checksumCache,
                                 AnsharConfiguration configuration,
                                 SiriObjectFactory siriObjectFactory,
                                 ParkingIdsService parkingIdsService,
                                 KafkaConfig kafkaConfig) {
        super(SiriDataType.FACILITY_MONITORING);
        this.facilityMonitoring = facilityMonitoring;
        this.changesMap = changesMap;
        this.lastUpdateRequested = lastUpdateRequested;
        this.checksumCache = checksumCache;
        this.configuration = configuration;
        this.siriObjectFactory = siriObjectFactory;
        this.parkingIdsService = parkingIdsService;
        this.kafkaConfig = kafkaConfig;
    }


    @Override
    public Collection<FacilityConditionStructure> getAll() {
        return facilityMonitoring.values();
    }

    @Override
    Map<SiriObjectStorageKey, FacilityConditionStructure> getAllAsMap() {
        return facilityMonitoring;
    }

    @Override
    public int getSize() {
        return facilityMonitoring.size();
    }

    @Override
    Collection<FacilityConditionStructure> getAllUpdates(String requestorId, String datasetId) {
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

                Collection<FacilityConditionStructure> changes = facilityMonitoring.getAll(datasetFilteredIdSet).values();

                Set<SiriObjectStorageKey> existingSet = changesMap.get(requestorId);
                if (existingSet == null) {
                    existingSet = new HashSet<>();
                }

                //Remove returned ids
                existingSet.removeAll(idSet);

                if (idSet.size() > facilityMonitoring.size()) {
                    //Remove outdated ids
                    existingSet.removeIf(id -> !facilityMonitoring.containsKey(id));
                }

                updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, existingSet, configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);

                log.debug("Returning {} changes to requestorRef {}", changes.size(), requestorId);
                return changes;
            } else {

                log.debug("Returning all to requestorRef {}", requestorId);
                updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, new HashSet<>(), configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);

            }
        }

        return getAll(datasetId);
    }

    private SiriObjectStorageKey createKey(String datasetId, FacilityConditionStructure conditionStructure) {
        StringBuilder key = new StringBuilder();
        key.append(conditionStructure.getFacilityRef() != null ? conditionStructure.getFacilityRef().getValue() : "null");
        FacilityLocationStructure facilityLocation = conditionStructure.getFacility() != null ? conditionStructure.getFacility().getFacilityLocation() : null;
        if (facilityLocation != null) {
            key.append(":")
                    .append(facilityLocation.getLineRef() != null ? facilityLocation.getLineRef().getValue() : "null")
                    .append(":")
                    .append(facilityLocation.getStopPointRef() != null ? facilityLocation.getStopPointRef().getValue() : "null")
                    .append(":")
                    .append(facilityLocation.getVehicleRef() != null ? facilityLocation.getVehicleRef().getValue() : "null")
                    .append(":")
                    .append(facilityLocation.getOperatorRef() != null ? facilityLocation.getOperatorRef().getValue() : "null");
        }
        return new SiriObjectStorageKey(datasetId, null, key.toString(), null, null, null, conditionStructure.getFacilityRef() != null ? conditionStructure.getFacilityRef().getValue() : null);
    }

    @Override
    public Collection<FacilityConditionStructure> addAll(String datasetId, List<FacilityConditionStructure> fmList) {
        Set<FacilityConditionStructure> addedData = new HashSet<>();
        Counter outDatedCounter = new CounterImpl(0);

        fmList.stream()
                .filter(Objects::nonNull)
                .forEach(fmCondition -> {
                    SiriObjectStorageKey key = createKey(datasetId, fmCondition);

                    long expiration = getExpiration(fmCondition);
                    String checksum = null;

                    try {
                        checksum = getChecksum(fmCondition);
                    } catch (NoSuchAlgorithmException e) {
                        log.warn("Error computing checksum, data will be updated", e);
                    }

                    if (checksum != null) {
                        String checksumFromCache = checksumCache.get(key);
                        if (Strings.CS.equals(checksum, checksumFromCache)) {
                            // FM is already in cache, do not update data
                            return;
                        }
                    }

                    if (expiration > 0) {
                        facilityMonitoring.set(key, fmCondition, expiration, TimeUnit.MILLISECONDS);
                        addedData.add(fmCondition);
                        if (checksum != null) {
                            checksumCache.set(key, checksum, expiration, TimeUnit.MILLISECONDS);
                        }
                    } else {
                        outDatedCounter.increment();
                    }
                });

        if (kafkaConfig.isKafkaEnabled() && kafkaConfig.isSendTrInSubscriptionDataToKafka()) {
            InputSubscriptionData isd = new InputSubscriptionData();
            isd.setDataset(datasetId);
            isd.setDataType(SiriDataType.FACILITY_MONITORING);
            isd.setNbElements(addedData.size());
            sendTrInSubscriptionDataToKafka.asyncRequestBody(sendTrInSubscriptionDataToKafka.getDefaultEndpoint(), isd);
        }

        return addedData;
    }

    @Override
    public Collection<FacilityConditionStructure> getAll(String datasetId) {
        if (datasetId == null) {
            return getAll();
        }

        return getValuesByDatasetId(facilityMonitoring, datasetId);
    }

    public Collection<FacilityConditionStructure> getAll(List<String> datasetsToRequest) {

        if (datasetsToRequest == null || datasetsToRequest.isEmpty()) {
            return getAll();
        }


        List<FacilityConditionStructure> results = new ArrayList<>();
        for (String dataset : datasetsToRequest) {
            results.addAll(getAll(dataset));
        }
        return results;
    }


    @Override
    public FacilityConditionStructure add(String datasetId, FacilityConditionStructure facilityCondition) {
        Collection<FacilityConditionStructure> added = addAll(datasetId, Collections.singletonList(facilityCondition));
        return !added.isEmpty() ? added.iterator().next() : null;
    }

    @Override
    long getExpiration(FacilityConditionStructure s) {
        ZonedDateTime validUntil = s.getValidityPeriod() != null ? s.getValidityPeriod().getEndTime() : null;
        if (s.getFacility() != null && validUntil == null) {
            validUntil = s.getFacility().getValidityCondition() != null && s.getFacility().getValidityCondition().getPeriods() != null &&
                    s.getFacility().getValidityCondition().getPeriods().stream()
                            .map(HalfOpenTimestampOutputRangeStructure::getEndTime)
                            .max(ChronoZonedDateTime::compareTo).isPresent() ?
                    s.getFacility().getValidityCondition().getPeriods().stream()
                            .map(HalfOpenTimestampOutputRangeStructure::getEndTime)
                            .max(ChronoZonedDateTime::compareTo).get() : null;
        }
        return validUntil == null ? ZonedDateTime.now().until(ZonedDateTime.now().plusYears(10), ChronoUnit.MILLIS) :
                ZonedDateTime.now().until(validUntil.plusMinutes(configuration.getFmGraceperiodMinutes()), ChronoUnit.MILLIS);
    }

    @Override
    public void clearAllByDatasetId(String datasetId) {
        Set<SiriObjectStorageKey> idsToRemove = facilityMonitoring.keySet(createCodespacePredicate(datasetId));
        log.warn("Removing all data ({} ids) for {}", idsToRemove.size(), datasetId);

        for (SiriObjectStorageKey id : idsToRemove) {
            facilityMonitoring.delete(id);
        }
        log.warn("Removing all data done");
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, String clientTrackingName, List<String> excludedDatasetIds, int maxSize,
                                      Set<String> requestedLineRef, Set<String> requestedFacilities, Set<String> requestedVehicleRef, Set<String> stopPointRef) {
        return createServiceDelivery(requestorId, datasetId, clientTrackingName, excludedDatasetIds, maxSize, requestedLineRef, requestedFacilities, requestedVehicleRef, stopPointRef, null);
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, String clientTrackingName, List<String> excludedDatasetIds, int maxSize,
                                      Set<String> requestedLineRef, Set<String> requestedFacilities, Set<String> requestedVehicleRef, Set<String> stopPointRef, String messageId) {


        int trackingPeriodMinutes = configuration.getTrackingPeriodMinutes();

        boolean isAdHocRequest = false;

        if (requestorId == null) {
            requestorId = UUID.randomUUID().toString();
            isAdHocRequest = true;
        }

        if (CollectionUtils.isNotEmpty(requestedFacilities)) {
            // map NETEX id to ORIGINAL id
            requestedFacilities = requestedFacilities.stream()
                    .map(rf -> parkingIdsService.getOriginalParkingId(rf).orElse(rf))
                    .collect(Collectors.toSet());
        }

        // Filter by datasetId
        Set<SiriObjectStorageKey> requestedIds = generateIdSet(datasetId, requestedFacilities, requestedLineRef,
                requestedVehicleRef, stopPointRef, excludedDatasetIds);

        long t1 = System.currentTimeMillis();

        Set<SiriObjectStorageKey> sizeLimitedIds = requestedIds.stream().limit(maxSize).collect(Collectors.toSet());
        log.info("Limiting size: {} ms", (System.currentTimeMillis() - t1));
        t1 = System.currentTimeMillis();

        Boolean isMoreData = sizeLimitedIds.size() < requestedIds.size();

        //Remove collected objects
        sizeLimitedIds.forEach(requestedIds::remove);
        log.info("Limiting size: {} ms", (System.currentTimeMillis() - t1));
        t1 = System.currentTimeMillis();

        Collection<FacilityConditionStructure> values = facilityMonitoring.getAll(sizeLimitedIds).values();
        log.info("Fetching data: {} ms", (System.currentTimeMillis() - t1));
        t1 = System.currentTimeMillis();

        Siri siri = siriObjectFactory.createFMServiceDelivery(values, requestorId, messageId);
        siri.getServiceDelivery().setMoreData(isMoreData);
        log.info("Creating SIRI-delivery: {} ms", (System.currentTimeMillis() - t1));

        if (!isAdHocRequest) {
            if (requestedIds.size() > facilityMonitoring.size()) {
                //Remove outdated ids
                requestedIds.removeIf(id -> !facilityMonitoring.containsKey(id));
            }
            //Update change-tracker
            updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, requestedIds, trackingPeriodMinutes, TimeUnit.MINUTES);
            log.info("Returning {}, {} left for requestorRef {}", sizeLimitedIds.size(), requestedIds.size(), requestorId);
        }

        return siri;
    }

    /**
     * Generates a set of keys that matches with user's request
     *
     * @param datasetId           dataset id
     * @param requestedFacilities requested facilities
     * @return a set of keys matching with filters
     */

    private Set<SiriObjectStorageKey> generateIdSet(String datasetId, Set<String> requestedFacilities, Set<String> requestedLineRef, Set<String> requestedVehicleRef,
                                                    Set<String> stopPointRef, List<String> excludedDatasetIds) {
        // Get all relevant ids
        Predicate<SiriObjectStorageKey, FacilityConditionStructure> predicate = SiriObjectStorageKeyUtil.getFacilityMonitoringPredicate(datasetId, requestedFacilities,
                requestedLineRef, requestedVehicleRef, stopPointRef, excludedDatasetIds);
        return new HashSet<>(facilityMonitoring.keySet(predicate));
    }

    public Set<String> getAllDatasetIds() {
        return facilityMonitoring.keySet().stream().map(SiriObjectStorageKey::getCodespaceId).collect(Collectors.toSet());
    }

    public Map<String, Integer> getDatasetSize() {
        Map<String, Integer> sizeMap = new HashMap<>();
        long t1 = System.currentTimeMillis();
        facilityMonitoring.keySet().forEach(key -> {
            String datasetId = key.getCodespaceId();

            Integer count = sizeMap.getOrDefault(datasetId, 0);
            sizeMap.put(datasetId, count + 1);
        });
        log.debug("Calculating data-distribution (FM) took {} ms: {}", (System.currentTimeMillis() - t1), sizeMap);
        return sizeMap;
    }


    public void clearAll() {
        log.error("Deleting all data - should only be used in test!!!");
        facilityMonitoring.clear();
        changesMap.clear();
        lastUpdateRequested.clear();
        cache.clear();
        checksumCache.clear();
    }
}
