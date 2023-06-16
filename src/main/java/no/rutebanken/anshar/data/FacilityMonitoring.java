package no.rutebanken.anshar.data;

import com.hazelcast.map.IMap;
import com.hazelcast.query.Predicate;
import net.sf.saxon.trans.SymbolicName;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.util.SiriObjectStorageKeyUtil;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.apache.kafka.common.network.Send;
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.ifopt.siri13.StopPlaceRef;
import uk.org.ifopt.siri20.StopPlaceComponentRefStructure;
import uk.org.siri.siri20.*;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getOriginalId;

@Repository
public class FacilityMonitoring extends SiriRepository<FacilityConditionStructure>  {

    private final Logger logger = LoggerFactory.getLogger(FacilityMonitoring.class);

    @Autowired
    @Qualifier("getFacilityMonitoring")
    private IMap<SiriObjectStorageKey, FacilityConditionStructure> facilityMonitoring;

    @Autowired
    @Qualifier("getFacilityMonitoringChangesMap")
    private IMap<String, Set<SiriObjectStorageKey>> changesMap;

    @Autowired
    @Qualifier("getLastFmUpdateRequest")
    private IMap<String, Instant> lastUpdateRequested;

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private SiriObjectFactory siriObjectFactory;


    protected FacilityMonitoring() {
        super(SiriDataType.FACILITY_MONITORING);
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
        return facilityMonitoring.keySet().size();
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

                logger.debug("Returning {} changes to requestorRef {}", changes.size(), requestorId);
                return changes;
            } else {

                logger.debug("Returning all to requestorRef {}", requestorId);
                updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, new HashSet<>(), configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);

            }
        }

        return getAll(datasetId);
    }

    private SiriObjectStorageKey createKey(String datasetId, FacilityConditionStructure conditionStructure) {
        StringBuilder key = new StringBuilder();
        key.append(conditionStructure.getFacilityRef() != null ? conditionStructure.getFacilityRef().getValue() : "null");
        FacilityLocationStructure facilityLocation = conditionStructure.getFacility() != null ? conditionStructure.getFacility().getFacilityLocation() : null;
        if (facilityLocation != null){
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

                    if(expiration > ZonedDateTime.now().toInstant().toEpochMilli()){
                        facilityMonitoring.set(key, fmCondition, expiration, TimeUnit.MILLISECONDS);
                        addedData.add(fmCondition);
                    }else{
                        outDatedCounter.increment();
                    }
                });

        return addedData;
    }

    @Override
    public Collection<FacilityConditionStructure> getAll(String datasetId) {
        if (datasetId == null) {
            return getAll();
        }

        return getValuesByDatasetId(facilityMonitoring, datasetId);
    }


    @Override
    public FacilityConditionStructure add(String datasetId, FacilityConditionStructure facilityCondition) {
        Collection<FacilityConditionStructure> added = addAll(datasetId, Arrays.asList(facilityCondition));
        return added.size() > 0 ? added.iterator().next() : null;
    }

    @Override
    long getExpiration(FacilityConditionStructure s) {
        ZonedDateTime validUntil = s.getValidityPeriod() != null ? s.getValidityPeriod().getEndTime() : null;
        if (s.getFacility() != null && validUntil == null){
            validUntil = s.getFacility().getValidityCondition() != null && s.getFacility().getValidityCondition().getPeriods() != null &&
                    s.getFacility().getValidityCondition().getPeriods().stream()
                    .map(HalfOpenTimestampOutputRangeStructure::getEndTime)
                    .max(ChronoZonedDateTime::compareTo).isPresent() ?
                    s.getFacility().getValidityCondition().getPeriods().stream()
                            .map(HalfOpenTimestampOutputRangeStructure::getEndTime)
                            .max(ChronoZonedDateTime::compareTo).get() : null;
        }
        return validUntil == null ? ZonedDateTime.now().until(ZonedDateTime.now().plusYears(10), ChronoUnit.MILLIS) :
                ZonedDateTime.now().until(validUntil.plus(configuration.getFMGraceperiodMinutes(), ChronoUnit.MINUTES), ChronoUnit.MILLIS);
    }

    @Override
    void clearAllByDatasetId(String datasetId) {
        Set<SiriObjectStorageKey> idsToRemove = facilityMonitoring.keySet(createCodespacePredicate(datasetId));
        logger.warn("Removing all data ({} ids) for {}", idsToRemove.size(), datasetId);

        for (SiriObjectStorageKey id : idsToRemove) {
            facilityMonitoring.delete(id);
        }
        logger.warn("Removing all data done");
    }


    public Siri createServiceDelivery(String requestorId, String datasetId, String clientTrackingName, List<String> excludedDatasetIds, int maxSize,
                                      Set<String> requestedLineRef, Set<String> requestedFacilities, Set<String> requestedVehicleRef, Set<String> stopPointRef) {

       requestorRefRepository.touchRequestorRef(requestorId, datasetId, clientTrackingName, SiriDataType.FACILITY_MONITORING);

        int trackingPeriodMinutes = configuration.getTrackingPeriodMinutes();

        boolean isAdHocRequest = false;

        if (requestorId == null) {
            requestorId = UUID.randomUUID().toString();
            isAdHocRequest = true;
        }

        // Filter by datasetId
        Set<SiriObjectStorageKey> requestedIds = generateIdSet(datasetId, requestedFacilities, requestedLineRef,
                requestedVehicleRef, stopPointRef, excludedDatasetIds);

        long t1 = System.currentTimeMillis();

        Set<SiriObjectStorageKey> sizeLimitedIds = requestedIds.stream().limit(maxSize).collect(Collectors.toSet());
        logger.info("Limiting size: {} ms", (System.currentTimeMillis() - t1));
        t1 = System.currentTimeMillis();

        Boolean isMoreData = sizeLimitedIds.size() < requestedIds.size();

        //Remove collected objects
        sizeLimitedIds.forEach(requestedIds::remove);
        logger.info("Limiting size: {} ms", (System.currentTimeMillis()-t1));
        t1 = System.currentTimeMillis();

        Collection<FacilityConditionStructure> values = facilityMonitoring.getAll(sizeLimitedIds).values();
        logger.info("Fetching data: {} ms", (System.currentTimeMillis()-t1));
        t1 = System.currentTimeMillis();

        Siri siri = siriObjectFactory.createFMServiceDelivery(values);
        siri.getServiceDelivery().setMoreData(isMoreData);
        logger.info("Creating SIRI-delivery: {} ms", (System.currentTimeMillis()-t1));

        if (isAdHocRequest) {
            logger.info("Returning {}, no requestorRef is set", sizeLimitedIds.size());
        } else {


            MessageRefStructure msgRef = new MessageRefStructure();
            msgRef.setValue(requestorId);
            siri.getServiceDelivery().setRequestMessageRef(msgRef);


            if (requestedIds.size() > facilityMonitoring.size()) {
                //Remove outdated ids
                requestedIds.removeIf(id -> !facilityMonitoring.containsKey(id));
            }

            //Update change-tracker
            updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, requestedIds, trackingPeriodMinutes, TimeUnit.MINUTES);

            logger.info("Returning {}, {} left for requestorRef {}", sizeLimitedIds.size(), requestedIds.size(), requestorId);
        }

        return siri;
    }

    /**
     * Generates a set of keys that matches with user's request
     *
     * @param datasetId         dataset id
     * @param requestedFacilities
     * @return a set of keys matching with filters
     */

    private Set<SiriObjectStorageKey> generateIdSet(String datasetId, Set<String> requestedFacilities, Set<String> requestedLineRef, Set<String> requestedVehicleRef,
                                                    Set<String> stopPointRef, List<String> excludedDatasetIds) {
        // Get all relevant ids

        Predicate<SiriObjectStorageKey, FacilityConditionStructure> predicate = SiriObjectStorageKeyUtil.getFacilityMonitoringPredicate(datasetId,requestedFacilities,
                requestedLineRef, requestedVehicleRef, stopPointRef, excludedDatasetIds);
        Set<SiriObjectStorageKey> idSet =new HashSet<>(facilityMonitoring.keySet(predicate));

        return idSet;
    }

    public void clearAll() {
        logger.error("Deleting all data - should only be used in test!!!");
        facilityMonitoring.clear();
        changesMap.clear();
        lastUpdateRequested.clear();
        cache.clear();
    }
}
