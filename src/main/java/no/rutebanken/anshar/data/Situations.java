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
import lombok.Getter;
import lombok.Setter;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.data.util.SiriObjectStorageKeyUtil;
import no.rutebanken.anshar.data.util.TimingTracer;
import no.rutebanken.anshar.routes.mapping.StopPlaceUpdaterService;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.ifopt.siri20.StopPlaceRef;
import uk.org.siri.siri20.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Repository
public class Situations extends SiriRepository<PtSituationElement> {
    private static final Logger logger = LoggerFactory.getLogger(Situations.class);

    @Getter
    @Setter
    @Autowired
    private IMap<SiriObjectStorageKey, PtSituationElement> situationElements;

    @Autowired
    @Qualifier("getSxChecksumMap")
    private IMap<SiriObjectStorageKey, String> checksumCache;

    @Autowired
    @Qualifier("getSituationChangesMap")
    private IMap<String, Set<SiriObjectStorageKey>> changesMap;


    @Autowired
    @Qualifier("getLastSxUpdateRequest")
    private IMap<String, Instant> lastUpdateRequested;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    ExtendedHazelcastService hazelcastService;

    @Autowired
    private StopPlaceUpdaterService stopPlaceService;

    protected Situations() {
        super(SiriDataType.SITUATION_EXCHANGE);
    }

//    @PostConstruct
//    private void initializeUpdateCommitter() {
//        super.initBufferCommitter(hazelcastService, lastUpdateRequested, changesMap, configuration.getChangeBufferCommitFrequency());
//    }

    /**
     * @return All situationElements
     */
    public Collection<PtSituationElement> getAll() {
        return situationElements.values();
    }

    public Map<SiriObjectStorageKey, PtSituationElement> getAllAsMap() {
        return situationElements;
    }

    public int getSize() {
        return situationElements.keySet().size();
    }


    public Map<String, Integer> getDatasetSize() {
        Map<String, Integer> sizeMap = new HashMap<>();
        long t1 = System.currentTimeMillis();
        situationElements.keySet().forEach(key -> {
            String datasetId = key.getCodespaceId();

            Integer count = sizeMap.getOrDefault(datasetId, 0);
            sizeMap.put(datasetId, count + 1);
        });
        logger.debug("Calculating data-distribution (SX) took {} ms: {}", (System.currentTimeMillis() - t1), sizeMap);
        return sizeMap;
    }


    public Map<String, Integer> getLocalDatasetSize() {
        Map<String, Integer> sizeMap = new HashMap<>();
        long t1 = System.currentTimeMillis();
        situationElements.localKeySet().forEach(key -> {
            String datasetId = key.getCodespaceId();

            Integer count = sizeMap.getOrDefault(datasetId, 0);
            sizeMap.put(datasetId, count + 1);
        });
        logger.debug("Calculating data-distribution (SX) took {} ms: {}", (System.currentTimeMillis() - t1), sizeMap);
        return sizeMap;
    }

    @Override
    public void clearAllByDatasetId(String datasetId) {

        Set<SiriObjectStorageKey> idsToRemove = situationElements.keySet(createCodespacePredicate(datasetId));

        logger.warn("Removing all data ({} ids) for {}", idsToRemove.size(), datasetId);

        for (SiriObjectStorageKey id : idsToRemove) {
            situationElements.remove(id);
            checksumCache.remove(id);
        }
    }

    public void clearAll() {
        logger.error("Deleting all data - should only be used in test!!!");
        situationElements.clear();
        checksumCache.clear();
        cache.clear();
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, String clientName, int maxSize) {

        requestorRefRepository.touchRequestorRef(requestorId, datasetId, clientName, SiriDataType.SITUATION_EXCHANGE);

        int trackingPeriodMinutes = configuration.getTrackingPeriodMinutes();

        boolean isAdHocRequest = false;

        if (requestorId == null) {
            requestorId = UUID.randomUUID().toString();
            isAdHocRequest = true;
        }

        // Filter by datasetId
        Set<SiriObjectStorageKey> requestedIds = generateIdSet(datasetId);

        long t1 = System.currentTimeMillis();

        Set<SiriObjectStorageKey> sizeLimitedIds = requestedIds.stream().limit(maxSize).collect(Collectors.toSet());
        logger.info("Limiting size: {} ms", (System.currentTimeMillis() - t1));
        t1 = System.currentTimeMillis();

        Boolean isMoreData = sizeLimitedIds.size() < requestedIds.size();

        //Remove collected objects
        sizeLimitedIds.forEach(requestedIds::remove);
        logger.info("Limiting size: {} ms", (System.currentTimeMillis() - t1));
        t1 = System.currentTimeMillis();

        Collection<PtSituationElement> values = situationElements.getAll(sizeLimitedIds).values();
        logger.info("Fetching data: {} ms", (System.currentTimeMillis() - t1));
        t1 = System.currentTimeMillis();

        Siri siri = siriObjectFactory.createSXServiceDelivery(values);
        siri.getServiceDelivery().setMoreData(isMoreData);
        logger.info("Creating SIRI-delivery: {} ms", (System.currentTimeMillis() - t1));

        if (isAdHocRequest) {
            logger.info("Returning {}, no requestorRef is set", sizeLimitedIds.size());
        } else {


            MessageRefStructure msgRef = new MessageRefStructure();
            msgRef.setValue(requestorId);
            siri.getServiceDelivery().setRequestMessageRef(msgRef);


            if (requestedIds.size() > situationElements.size()) {
                //Remove outdated ids
                requestedIds.removeIf(id -> !situationElements.containsKey(id));
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
     * @param datasetId dataset id
     * @return a set of keys matching with filters
     */
    private Set<SiriObjectStorageKey> generateIdSet(String datasetId) {
        // Get all relevant ids
        Predicate<SiriObjectStorageKey, PtSituationElement> predicate = SiriObjectStorageKeyUtil.getSituationExchangePredicate(datasetId);
        return new HashSet<>(situationElements.keySet(predicate));
    }


    /**
     * @return All vehicle activities that are still valid
     */
    public Collection<PtSituationElement> getAll(String datasetId) {
        if (datasetId == null) {
            return getAll();
        }

        return getValuesByDatasetId(situationElements, datasetId);
    }


    /**
     * @return All vehicle activities that have been updated since last request from requestor
     */
    public Collection<PtSituationElement> getAllUpdates(String requestorId, String datasetId) {
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
                Collection<PtSituationElement> changes = situationElements.getAll(datasetFilteredIdSet).values();

                // Data may have been updated
                Set<SiriObjectStorageKey> existingSet = changesMap.get(requestorId);
                if (existingSet == null) {
                    existingSet = new HashSet<>();
                }
                //Remove returned ids
                existingSet.removeAll(idSet);

                updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, existingSet, configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);

                logger.info("Returning {} changes to requestorRef {}", changes.size(), requestorId);
                return changes;
            } else {
                logger.info("Returning all to requestorRef {}", requestorId);
            }

            updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, new HashSet<>(), configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);

        }

        return getAll(datasetId);
    }

    public long getExpiration(PtSituationElement situationElement) {
        List<HalfOpenTimestampOutputRangeStructure> validityPeriods = situationElement.getValidityPeriods();

        ZonedDateTime expiry = null;

        if (validityPeriods != null) {
            for (HalfOpenTimestampOutputRangeStructure validity : validityPeriods) {

                //Find latest validity
                if (expiry == null) {
                    expiry = validity.getEndTime();
                } else if (validity != null && validity.getEndTime().isAfter(expiry)) {
                    expiry = validity.getEndTime();
                }
            }
        }

        if (expiry != null && expiry.getYear() < 2100) {
            return ZonedDateTime.now().until(expiry.plusMinutes(configuration.getSxGraceperiodMinutes()), ChronoUnit.MILLIS);
        } else {
            // No expiration set - keep "forever"
            return ZonedDateTime.now().until(ZonedDateTime.now().plusYears(10), ChronoUnit.MILLIS);
        }
    }

    public Collection<PtSituationElement> addAll(String datasetId, List<PtSituationElement> sxList) {
        Map<SiriObjectStorageKey, PtSituationElement> changes = new HashMap<>();
        Map<SiriObjectStorageKey, String> checksumTmp = new HashMap<>();

        Counter alreadyExpiredCounter = new CounterImpl(0);
        Counter ignoredCounter = new CounterImpl(0);
        sxList.forEach(situation -> {
            TimingTracer timingTracer = new TimingTracer("single-sx");


            if (situation.getParticipantRef() == null) {
                RequestorRef emptyReqRef = new RequestorRef();
                emptyReqRef.setValue("Empty participant ref");
                situation.setParticipantRef(emptyReqRef);
            }

            SiriObjectStorageKey key = createKey(datasetId, situation);
            timingTracer.mark("createKey");

            long expiration = getExpiration(situation);
            if (expiration < 0 && situationElements.containsKey(key)) {
                situationElements.remove(key);
                checksumCache.remove(key);
            }

            String currentChecksum = null;
            try {
                currentChecksum = getChecksum(situation);
                timingTracer.mark("getChecksum");
            } catch (Exception e) {
                //Ignore - data will be updated
            }

            String existingChecksum = checksumCache.get(key);
            timingTracer.mark("checksumCache.get");
            boolean updated;
            if (existingChecksum != null && situationElements.containsKey(key)) { // Checksum not compared if actual situation does not exist
                //Exists - compare values
                updated = !Objects.equals(currentChecksum, existingChecksum);
            } else {
                //Does not exist
                updated = true;
            }
            timingTracer.mark("compareChecksum");
            updated = defineAffectedPoints(situation, datasetId) || updated;

            if (keepByProgressStatus(situation) && updated) {
                timingTracer.mark("keepByProgressStatus");
                timingTracer.mark("getExpiration");
                if (expiration > 0) { //expiration < 0 => already expired
                    changes.put(key, situation);
                    checksumTmp.put(key, currentChecksum);
                    situationElements.set(key, situation, expiration, TimeUnit.MILLISECONDS);
                } else if (situationElements.containsKey(key)) {
                    // Situation is no longer valid
                    situationElements.delete(key);
                    timingTracer.mark("situationElements.delete");
                    checksumCache.remove(key);
                    timingTracer.mark("checksumCache.remove");
                }
                if (expiration < 0) {
                    alreadyExpiredCounter.increment();
                }
            } else {
                ignoredCounter.increment();
            }

            long elapsed = timingTracer.getTotalTime();
            if (elapsed > 500) {
                logger.info("Adding SX-object with key {} took {} ms: {}", key, elapsed, timingTracer);
            }
        });
        TimingTracer timingTracer = new TimingTracer("all-sx [" + changes.size() + " changes]");

        logger.debug("Updated {} (of {}) :: Already expired: {}, Unchanged: {}", changes.size(), sxList.size(), alreadyExpiredCounter.getValue(), ignoredCounter.getValue());

        checksumCache.setAll(checksumTmp);
        timingTracer.mark("checksumCache.setAll");


        markDataReceived(SiriDataType.SITUATION_EXCHANGE, datasetId, sxList.size(), changes.size(), alreadyExpiredCounter.getValue(), ignoredCounter.getValue());
        timingTracer.mark("markDataReceived");

        markIdsAsUpdated(changes.keySet());
        timingTracer.mark("markIdsAsUpdated");

        if (timingTracer.getTotalTime() > 1000) {
            logger.info(timingTracer.toString());
        }

        return changes.values();
    }

    private boolean defineAffectedPoints(PtSituationElement situation, String datasetId) {

        if (situation.getAffects() == null || situation.getAffects().getStopPoints() == null
                || situation.getAffects().getStopPoints().getAffectedStopPoints() == null) {
            return false;
        }
        List<AffectedStopPointStructure> refId = situation.getAffects().getStopPoints().getAffectedStopPoints();
        List<AffectedStopPointStructure> refIdStopPlace = refId.stream()
                .filter(affectedStopPoint -> affectedStopPoint.getStopPointRef() != null && stopPlaceService.isKnownId(datasetId + ":StopPlace:" + affectedStopPoint.getStopPointRef().getValue()))
                .collect(Collectors.toList());
        List<AffectedStopPlaceStructure> affectedStopPlaceStructures = new ArrayList<>();

        refIdStopPlace.forEach(affectedStopPoint -> {
            AffectedStopPlaceStructure affectedStopPlaceStructure = new AffectedStopPlaceStructure();
            StopPlaceRef stopPlaceRef = new StopPlaceRef();
            stopPlaceRef.setValue(affectedStopPoint.getStopPointRef().getValue());
            affectedStopPlaceStructure.setStopPlaceRef(stopPlaceRef);
            affectedStopPlaceStructures.add(affectedStopPlaceStructure);
        });

        situation.getAffects().getStopPoints().getAffectedStopPoints().removeAll(refIdStopPlace);
        if (!affectedStopPlaceStructures.isEmpty()) {

            AffectsScopeStructure.StopPlaces newStopPlaces = new AffectsScopeStructure.StopPlaces();
            newStopPlaces.getAffectedStopPlaces().addAll(affectedStopPlaceStructures);
            situation.getAffects().setStopPlaces(newStopPlaces);
            return true;
        }
        return false;
    }


    public void removeSituation(String datasetId, PtSituationElement situation) {

        SiriObjectStorageKey key = createKey(datasetId, situation);
        situationElements.delete(key);
        checksumCache.remove(key);
    }

    private boolean keepByProgressStatus(PtSituationElement situation) {
        if (situation.getProgress() != null) {
            switch (situation.getProgress()) {
                case APPROVED_DRAFT:
                case DRAFT:
                    return false;
                case CLOSED:
                case OPEN:
                case CLOSING:
                case PUBLISHED:
                    return true;
            }
        }
        // Keep by default
        return true;
    }

    public PtSituationElement add(String datasetId, PtSituationElement situation) {
        if (situation == null) {
            return null;
        }
        List<PtSituationElement> situationList = new ArrayList<>();
        situationList.add(situation);
        addAll(datasetId, situationList);
        return situationElements.get(createKey(datasetId, situation));
    }

    private static SiriObjectStorageKey createKey(String datasetId, PtSituationElement element) {
        String situationNumber = element.getSituationNumber() != null ? element.getSituationNumber().getValue() : "null";
        return new SiriObjectStorageKey(datasetId, null, String.format("%s:%s", datasetId, situationNumber));
    }

    public void cleanChangesMap() {
        changesMap.clear();
    }

    public Set<String> getAllDatasetIds() {
        return situationElements.keySet().stream().map(SiriObjectStorageKey::getCodespaceId).collect(Collectors.toSet());
    }

}
