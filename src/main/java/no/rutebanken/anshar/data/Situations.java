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
import com.hazelcast.core.ReplicatedMap;
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
import uk.org.siri.siri20.HalfOpenTimestampOutputRangeStructure;
import uk.org.siri.siri20.MessageRefStructure;
import uk.org.siri.siri20.PtSituationElement;
import uk.org.siri.siri20.Siri;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Repository
public class Situations extends SiriRepository<PtSituationElement> {
    private final Logger logger = LoggerFactory.getLogger(Situations.class);

    @Autowired
    private IMap<String,PtSituationElement> situations;

    @Autowired
    @Qualifier("getSxChecksumMap")
    private ReplicatedMap<String,String> checksumCache;

    @Autowired
    @Qualifier("getSituationChangesMap")
    private IMap<String, Set<String>> changesMap;


    @Autowired
    @Qualifier("getLastSxUpdateRequest")
    private IMap<String, Instant> lastUpdateRequested;

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private RequestorRefRepository requestorRefRepository;

    @Autowired
    ExtendedHazelcastService hazelcastService;

    @PostConstruct
    private void initializeUpdateCommitter() {
        super.initBufferCommitter(hazelcastService, lastUpdateRequested, changesMap, configuration.getChangeBufferCommitFrequency());
    }

    /**
     * @return All situations
     */
    public Collection<PtSituationElement> getAll() {
        return situations.values();
    }

    public int getSize() {
        return situations.keySet().size();
    }


    public Map<String, Integer> getDatasetSize() {
        Map<String, Integer> sizeMap = new HashMap<>();
        long t1 = System.currentTimeMillis();
        situations.keySet().forEach(key -> {
            String datasetId = key.substring(0, key.indexOf(":"));

            Integer count = sizeMap.getOrDefault(datasetId, 0);
            sizeMap.put(datasetId, count+1);
        });
        logger.debug("Calculating data-distribution (SX) took {} ms: {}", (System.currentTimeMillis()-t1), sizeMap);
        return sizeMap;
    }


    public Map<String, Integer> getLocalDatasetSize() {
        Map<String, Integer> sizeMap = new HashMap<>();
        long t1 = System.currentTimeMillis();
        situations.localKeySet().forEach(key -> {
            String datasetId = key.substring(0, key.indexOf(":"));

            Integer count = sizeMap.getOrDefault(datasetId, 0);
            sizeMap.put(datasetId, count+1);
        });
        logger.debug("Calculating data-distribution (SX) took {} ms: {}", (System.currentTimeMillis()-t1), sizeMap);
        return sizeMap;
    }


    public Integer getDatasetSize(String datasetId) {
        return Math.toIntExact(situations.keySet().stream()
                .filter(key -> datasetId.equals(key.substring(0, key.indexOf(":"))))
                .count());
    }

    @Override
    public void clearAllByDatasetId(String datasetId) {
        String prefix = datasetId + ":";
        Set<String> idsToRemove = situations.keySet()
                .stream()
                .filter(key -> key.startsWith(prefix))
                .collect(Collectors.toSet());

        logger.warn("Removing all data ({} ids) for {}", idsToRemove.size(), datasetId);

        for (String id : idsToRemove) {
            situations.remove(id);
            checksumCache.remove(id);
        }
    }

    public void clearAll() {
        logger.error("Deleting all data - should only be used in test!!!");
        situations.clear();
        checksumCache.clear();
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, String clientName, int maxSize) {

        requestorRefRepository.touchRequestorRef(requestorId, datasetId, clientName, SiriDataType.SITUATION_EXCHANGE);

        int trackingPeriodMinutes = configuration.getTrackingPeriodMinutes();

        boolean isAdHocRequest = false;

        if (requestorId == null) {
            requestorId = UUID.randomUUID().toString();
            isAdHocRequest = true;
        }

        // Get all relevant ids
        Set<String> allIds = new HashSet<>();
        Set<String> idSet = changesMap.getOrDefault(requestorId, allIds);

        if (idSet == allIds) {
            idSet.addAll(situations.keySet());
        }

        //Filter by datasetId
        Set<String> requestedIds = idSet.stream()
                .filter(key -> datasetId == null || key.startsWith(datasetId + ":"))
                .collect(Collectors.toSet());
        long t1 = System.currentTimeMillis();

        Set<String> sizeLimitedIds = requestedIds.stream().limit(maxSize).collect(Collectors.toSet());
        logger.info("Limiting size: {} ms", (System.currentTimeMillis()-t1));
        t1 = System.currentTimeMillis();

        Boolean isMoreData = sizeLimitedIds.size() < requestedIds.size();

        //Remove collected objects
        sizeLimitedIds.forEach(idSet::remove);
        logger.info("Limiting size: {} ms", (System.currentTimeMillis()-t1));
        t1 = System.currentTimeMillis();

        Collection<PtSituationElement> values = getValuesByIds(situations, sizeLimitedIds);
        logger.info("Fetching data: {} ms", (System.currentTimeMillis()-t1));
        t1 = System.currentTimeMillis();

        Siri siri = siriObjectFactory.createSXServiceDelivery(values);
        siri.getServiceDelivery().setMoreData(isMoreData);
        logger.info("Creating SIRI-delivery: {} ms", (System.currentTimeMillis()-t1));

        if (isAdHocRequest) {
            logger.info("Returning {}, no requestorRef is set", sizeLimitedIds.size());
        } else {


            MessageRefStructure msgRef = new MessageRefStructure();
            msgRef.setValue(requestorId);
            siri.getServiceDelivery().setRequestMessageRef(msgRef);


            if (idSet.size() > situations.size()) {
                //Remove outdated ids
                idSet.removeIf(id -> !situations.containsKey(id));
            }

            //Update change-tracker
            updateChangeTrackers(lastUpdateRequested, changesMap, requestorId, idSet, trackingPeriodMinutes, TimeUnit.MINUTES);

            logger.info("Returning {}, {} left for requestorRef {}", sizeLimitedIds.size(), idSet.size(), requestorId);
        }

        return siri;
    }
    /**
     * @return All vehicle activities that are still valid
     */
    public Collection<PtSituationElement> getAll(String datasetId) {
        if (datasetId == null) {
            return getAll();
        }

        return getValuesByDatasetId(situations, datasetId);
    }


    /**
     * @return All vehicle activities that have been updated since last request from requestor
     */
    public Collection<PtSituationElement> getAllUpdates(String requestorId, String datasetId) {
        if (requestorId != null) {

            Set<String> idSet = changesMap.get(requestorId);
            lastUpdateRequested.set(requestorId, Instant.now(), configuration.getTrackingPeriodMinutes(), TimeUnit.MINUTES);
            if (idSet != null) {
                Set<String> datasetFilteredIdSet = new HashSet<>();

                if (datasetId != null) {
                    idSet.stream().filter(key -> key.startsWith(datasetId + ":")).forEach(datasetFilteredIdSet::add);
                } else {
                    datasetFilteredIdSet.addAll(idSet);
                }
                Collection<PtSituationElement> changes = getValuesByIds(situations, datasetFilteredIdSet);

                // Data may have been updated
                Set<String> existingSet = changesMap.get(requestorId);
                if (existingSet == null) {
                    existingSet = new HashSet<>();
                }
                //Remove returned ids
                existingSet.removeAll(idSet);

                //Remove outdated ids
                existingSet.removeIf(id -> !situations.containsKey(id));

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

        if (expiry != null) {
            return ZonedDateTime.now().until(expiry.plus(configuration.getSxGraceperiodMinutes(), ChronoUnit.MINUTES), ChronoUnit.MILLIS);
        } else {
            // No expiration set - keep "forever"
            return  ZonedDateTime.now().until(ZonedDateTime.now().plusYears(10), ChronoUnit.MILLIS);
        }
    }

    public Collection<PtSituationElement> addAll(String datasetId, List<PtSituationElement> sxList) {
        Set<String> changes = new HashSet<>();
        Set<PtSituationElement> addedData = new HashSet<>();

        Counter alreadyExpiredCounter = new CounterImpl(0);
        Counter ignoredCounter = new CounterImpl(0);
        sxList.forEach(situation -> {
            String key = createKey(datasetId, situation);

            String currentChecksum = null;
            try {
                currentChecksum = getChecksum(situation);
            } catch (Exception e) {
                //Ignore - data will be updated
            }

            String existingChecksum = checksumCache.get(key);
            boolean updated;
            if (existingChecksum != null) {
                //Exists - compare values
                updated =  !(currentChecksum.equals(existingChecksum));
            } else {
                //Does not exist
                updated = true;
            }

            if (updated) {
                long expiration = getExpiration(situation);
                if (expiration > 0) { //expiration < 0 => already expired
                    situations.set(key, situation, expiration, TimeUnit.MILLISECONDS);
                    checksumCache.put(key, currentChecksum, expiration, TimeUnit.MILLISECONDS);
                    changes.add(key);
                    addedData.add(situation);
                } else if (situations.containsKey(key)) {
                    // Situation is no longer valid
                    situations.delete(key);
                    checksumCache.remove(key);
                }
                if (expiration < 0) {
                    alreadyExpiredCounter.increment();
                }
            } else {
                ignoredCounter.increment();
            }

        });
        logger.info("Updated {} (of {}) :: Already expired: {}, Unchanged: {}", changes.size(), sxList.size(), alreadyExpiredCounter.getValue(), ignoredCounter.getValue());

        markDataReceived(SiriDataType.SITUATION_EXCHANGE, datasetId, sxList.size(), changes.size(), alreadyExpiredCounter.getValue(), ignoredCounter.getValue());

        markIdsAsUpdated(changes);

        return addedData;
    }

    public PtSituationElement add(String datasetId, PtSituationElement situation) {
        if (situation == null) {
            return null;
        }
        List<PtSituationElement> situationList = new ArrayList<>();
        situationList.add(situation);
        addAll(datasetId, situationList);
        return situations.get(createKey(datasetId, situation));
    }

    private static String createKey(String datasetId, PtSituationElement element) {
        StringBuilder key = new StringBuilder();

        key.append(datasetId).append(":")
                .append((element.getSituationNumber() != null ? element.getSituationNumber().getValue() : "null"))
                .append(":")
                .append((element.getParticipantRef() != null ? element.getParticipantRef().getValue() :"null"));
        return key.toString();
    }
}
