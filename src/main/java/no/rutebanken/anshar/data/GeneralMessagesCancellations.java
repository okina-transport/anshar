package no.rutebanken.anshar.data;

import com.hazelcast.map.IMap;
import com.hazelcast.query.Predicate;
import no.rutebanken.anshar.config.AnsharConfiguration;
import no.rutebanken.anshar.data.util.SiriObjectStorageKeyUtil;
import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.quartz.utils.counter.Counter;
import org.quartz.utils.counter.CounterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import uk.org.siri.siri20.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Repository
public class GeneralMessagesCancellations extends SiriRepository<GeneralMessageCancellation> {

    private static final Logger logger = LoggerFactory.getLogger(GeneralMessagesCancellations.class);
    

    @Autowired
    @Qualifier("getGeneralMessageCancellations")
    private IMap<SiriObjectStorageKey, GeneralMessageCancellation> generalMessagesCancellations;


    @Autowired
    @Qualifier("getGeneralMessageCancellationChangesMap")
    private IMap<String, Set<SiriObjectStorageKey>> changesMap;

    @Autowired
    @Qualifier("getLastGmUpdateRequest")
    private IMap<String, Instant> lastUpdateRequested;

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private SiriObjectFactory siriObjectFactory;


    protected GeneralMessagesCancellations() {
        super(SiriDataType.GENERAL_MESSAGE);
    }


    @Override
    public Collection<GeneralMessageCancellation> getAll() {
        return generalMessagesCancellations.values();
    }

    @Override
    Map<SiriObjectStorageKey, GeneralMessageCancellation> getAllAsMap() {
        return generalMessagesCancellations;
    }

    @Override
    public int getSize() {
        return generalMessagesCancellations.keySet().size();
    }

    @Override
    public Collection<GeneralMessageCancellation> getAll(String datasetId) {
        if (datasetId == null) {
            return getAll();
        }

        return getValuesByDatasetId(generalMessagesCancellations, datasetId);
    }

    @Override
    Collection<GeneralMessageCancellation> getAllUpdates(String requestorId, String datasetId) {
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

                Collection<GeneralMessageCancellation> changes = generalMessagesCancellations.getAll(datasetFilteredIdSet).values();

                Set<SiriObjectStorageKey> existingSet = changesMap.get(requestorId);
                if (existingSet == null) {
                    existingSet = new HashSet<>();
                }

                //Remove returned ids
                existingSet.removeAll(idSet);

                if (idSet.size() > generalMessagesCancellations.size()) {
                    //Remove outdated ids
                    existingSet.removeIf(id -> !generalMessagesCancellations.containsKey(id));
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

    @Override
    public Collection<GeneralMessageCancellation> addAll(String datasetId, List<GeneralMessageCancellation> gmList) {
        Set<GeneralMessageCancellation> addedData = new HashSet<>();
        Counter outdatedCounter = new CounterImpl(0);

        gmList.stream()
                .filter(generalMessage -> generalMessage != null)
                .filter(generalMessage -> generalMessage.getInfoMessageIdentifier() != null)
                .filter(generalMessage -> generalMessage.getInfoChannelRef() != null)
                .forEach(generalMessage -> {

                    if (generalMessage.getRecordedAtTime() == null) {
                        generalMessage.setRecordedAtTime(ZonedDateTime.now());
                    }

                    if (generalMessage.getItemRef() == null) {
                        ItemRefStructure refStruct = new ItemRefStructure();
                        refStruct.setValue(UUID.randomUUID().toString());
                        generalMessage.setItemRef(refStruct);
                    }


                    SiriObjectStorageKey key = createKey(datasetId, generalMessage);

                    long expiration = getExpiration(generalMessage);

                    if (expiration > 0) {
                        generalMessagesCancellations.set(key, generalMessage, expiration, TimeUnit.MILLISECONDS);
                        addedData.add(generalMessage);
                    } else {
                        outdatedCounter.increment();
                    }

                });

        logger.debug("Updated {} (of {}) :: Ignored elements - outdated : {}", addedData.size(), gmList.size(), outdatedCounter.getValue());
        markDataReceived(SiriDataType.GENERAL_MESSAGE, datasetId, gmList.size(), addedData.size(), outdatedCounter.getValue(), 0);

        return addedData;
    }

    @Override
    public GeneralMessageCancellation add(String datasetId, GeneralMessageCancellation generalMessage) {
        Collection<GeneralMessageCancellation> added = addAll(datasetId, Arrays.asList(generalMessage));
        return added.size() > 0 ? added.iterator().next() : null;
    }

    @Override
    long getExpiration(GeneralMessageCancellation s) {
        return 10000000;
    }


    @Override
    void clearAllByDatasetId(String datasetId) {
        Set<SiriObjectStorageKey> idsToRemove = generalMessagesCancellations.keySet(createCodespacePredicate(datasetId));
        logger.warn("Removing all data ({} ids) for {}", idsToRemove.size(), datasetId);

        for (SiriObjectStorageKey id : idsToRemove) {
            generalMessagesCancellations.delete(id);
        }
        logger.warn("Removing all data done");
    }

    private SiriObjectStorageKey createKey(String datasetId, GeneralMessageCancellation generalMessage) {
        return new SiriObjectStorageKey(datasetId, null, generalMessage.getInfoMessageIdentifier().getValue(), null, null, generalMessage.getInfoChannelRef().getValue());
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, String clientName, int maxSize, List<InfoChannelRefStructure> requestedChannels) {

        requestorRefRepository.touchRequestorRef(requestorId, datasetId, clientName, SiriDataType.GENERAL_MESSAGE);

        int trackingPeriodMinutes = configuration.getTrackingPeriodMinutes();

        boolean isAdHocRequest = false;

        if (requestorId == null) {
            requestorId = UUID.randomUUID().toString();
            isAdHocRequest = true;
        }

        // Filter by datasetId
        Set<SiriObjectStorageKey> requestedIds = generateIdSet(datasetId, requestedChannels);

        long t1 = System.currentTimeMillis();

        Set<SiriObjectStorageKey> sizeLimitedIds = requestedIds.stream().limit(maxSize).collect(Collectors.toSet());
        logger.info("Limiting size: {} ms", (System.currentTimeMillis() - t1));
        t1 = System.currentTimeMillis();

        Boolean isMoreData = sizeLimitedIds.size() < requestedIds.size();

        //Remove collected objects
        sizeLimitedIds.forEach(requestedIds::remove);
        logger.info("Limiting size: {} ms", (System.currentTimeMillis() - t1));
        t1 = System.currentTimeMillis();

        Collection<GeneralMessageCancellation> values = generalMessagesCancellations.getAll(sizeLimitedIds).values();
        logger.info("Fetching data: {} ms", (System.currentTimeMillis() - t1));
        t1 = System.currentTimeMillis();

        Siri siri = siriObjectFactory.createGMCancellationServiceDelivery(values);
        siri.getServiceDelivery().setMoreData(isMoreData);
        logger.info("Creating SIRI-delivery: {} ms", (System.currentTimeMillis() - t1));

        if (isAdHocRequest) {
            logger.info("Returning {}, no requestorRef is set", sizeLimitedIds.size());
        } else {


            MessageRefStructure msgRef = new MessageRefStructure();
            msgRef.setValue(requestorId);
            siri.getServiceDelivery().setRequestMessageRef(msgRef);


            if (requestedIds.size() > generalMessagesCancellations.size()) {
                //Remove outdated ids
                requestedIds.removeIf(id -> !generalMessagesCancellations.containsKey(id));
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
     * @param requestedChannels
     * @return a set of keys matching with filters
     */
    private Set<SiriObjectStorageKey> generateIdSet(String datasetId, List<InfoChannelRefStructure> requestedChannels) {

        // Get all relevant ids

        Predicate<SiriObjectStorageKey, GeneralMessageCancellation> predicate = SiriObjectStorageKeyUtil.getGeneralMessageCancellationsPredicate(datasetId, requestedChannels);
        Set<SiriObjectStorageKey> idSet = new HashSet<>(generalMessagesCancellations.keySet(predicate));

        return idSet;
    }

    public void clearAll() {
        logger.error("Deleting all data - should only be used in test!!!");
        generalMessagesCancellations.clear();
        changesMap.clear();
        lastUpdateRequested.clear();
        cache.clear();
    }
}
