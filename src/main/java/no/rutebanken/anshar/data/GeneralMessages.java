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
import uk.org.siri.siri20.GeneralMessage;
import uk.org.siri.siri20.InfoChannelRefStructure;
import uk.org.siri.siri20.MessageRefStructure;
import uk.org.siri.siri20.Siri;


import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Repository
public class GeneralMessages extends SiriRepository<GeneralMessage> {

    private static final Logger logger = LoggerFactory.getLogger(GeneralMessages.class);

    @Autowired
    @Qualifier("getGeneralMessages")
    private IMap<SiriObjectStorageKey, GeneralMessage> generalMessages;


    @Autowired
    @Qualifier("getGeneralMessagesChangesMap")
    private IMap<String, Set<SiriObjectStorageKey>> changesMap;

    @Autowired
    @Qualifier("getLastGmUpdateRequest")
    private IMap<String, Instant> lastUpdateRequested;

    @Autowired
    private AnsharConfiguration configuration;

    @Autowired
    private SiriObjectFactory siriObjectFactory;


    protected GeneralMessages() {
        super(SiriDataType.GENERAL_MESSAGE);
    }


    @Override
    public Collection<GeneralMessage> getAll() {
        return generalMessages.values();
    }

    @Override
    Map<SiriObjectStorageKey, GeneralMessage> getAllAsMap() {
        return generalMessages;
    }

    @Override
    public int getSize() {
        return generalMessages.keySet().size();
    }

    @Override
    public Collection<GeneralMessage> getAll(String datasetId) {
        if (datasetId == null) {
            return getAll();
        }

        return getValuesByDatasetId(generalMessages, datasetId);
    }

    @Override
    Collection<GeneralMessage> getAllUpdates(String requestorId, String datasetId) {
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

                Collection<GeneralMessage> changes = generalMessages.getAll(datasetFilteredIdSet).values();

                Set<SiriObjectStorageKey> existingSet = changesMap.get(requestorId);
                if (existingSet == null) {
                    existingSet = new HashSet<>();
                }

                //Remove returned ids
                existingSet.removeAll(idSet);

                if (idSet.size() > generalMessages.size()) {
                    //Remove outdated ids
                    existingSet.removeIf(id -> !generalMessages.containsKey(id));
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
    public Collection<GeneralMessage> addAll(String datasetId, List<GeneralMessage> gmList) {
        Set<GeneralMessage> addedData = new HashSet<>();
        Counter outdatedCounter = new CounterImpl(0);

        gmList.stream()
                .filter(generalMessage -> generalMessage != null)
                .filter(generalMessage -> generalMessage.getInfoMessageIdentifier() != null)
                .filter(generalMessage -> generalMessage.getInfoChannelRef() != null)
                .forEach(generalMessage -> {

                    SiriObjectStorageKey key = createKey(datasetId, generalMessage);

                    long expiration = getExpiration(generalMessage);

                    if (expiration > 0 ) {
                        generalMessages.set(key, generalMessage, expiration, TimeUnit.MILLISECONDS);
                        addedData.add(generalMessage);
                    }else{
                        outdatedCounter.increment();
                    }

                });

        logger.debug("Updated {} (of {}) :: Ignored elements - outdated : {}", addedData.size(), gmList.size(), outdatedCounter.getValue());
        markDataReceived(SiriDataType.GENERAL_MESSAGE, datasetId, gmList.size(), addedData.size(),outdatedCounter.getValue(),0);

        return addedData;
    }

    @Override
    public GeneralMessage add(String datasetId, GeneralMessage generalMessage) {
        Collection<GeneralMessage> added = addAll(datasetId, Arrays.asList(generalMessage));
        return added.size() > 0 ? added.iterator().next() : null;
    }

    @Override
    public long getExpiration(GeneralMessage s) {
        ZonedDateTime validUntil = s.getValidUntilTime();
        return validUntil == null ? ZonedDateTime.now().until(ZonedDateTime.now().plusYears(10), ChronoUnit.MILLIS) :
                ZonedDateTime.now().until(validUntil.plus(configuration.getSxGraceperiodMinutes(), ChronoUnit.MINUTES), ChronoUnit.MILLIS);
    }

    @Override
    void clearAllByDatasetId(String datasetId) {
        Set<SiriObjectStorageKey> idsToRemove = generalMessages.keySet(createCodespacePredicate(datasetId));
        logger.warn("Removing all data ({} ids) for {}", idsToRemove.size(), datasetId);

        for (SiriObjectStorageKey id : idsToRemove) {
            generalMessages.delete(id);
        }
        logger.warn("Removing all data done");
    }

    private SiriObjectStorageKey createKey(String datasetId, GeneralMessage generalMessage) {
        return new SiriObjectStorageKey(datasetId, null, generalMessage.getInfoMessageIdentifier().getValue(), null, null,generalMessage.getInfoChannelRef().getValue());
    }

    public Siri createServiceDelivery(String requestorId, String datasetId, String clientName, int maxSize,  List<InfoChannelRefStructure> requestedChannels) {

        requestorRefRepository.touchRequestorRef(requestorId, datasetId, clientName, SiriDataType.GENERAL_MESSAGE);

        int trackingPeriodMinutes = configuration.getTrackingPeriodMinutes();

        boolean isAdHocRequest = false;

        if (requestorId == null) {
            requestorId = UUID.randomUUID().toString();
            isAdHocRequest = true;
        }

        // Filter by datasetId
        Set<SiriObjectStorageKey> requestedIds = generateIdSet(datasetId,requestedChannels);

        long t1 = System.currentTimeMillis();

        Set<SiriObjectStorageKey> sizeLimitedIds = requestedIds.stream().limit(maxSize).collect(Collectors.toSet());
        logger.info("Limiting size: {} ms", (System.currentTimeMillis() - t1));
        t1 = System.currentTimeMillis();

        Boolean isMoreData = sizeLimitedIds.size() < requestedIds.size();

        //Remove collected objects
        sizeLimitedIds.forEach(requestedIds::remove);
        logger.info("Limiting size: {} ms", (System.currentTimeMillis()-t1));
        t1 = System.currentTimeMillis();

        Collection<GeneralMessage> values = generalMessages.getAll(sizeLimitedIds).values();
        logger.info("Fetching data: {} ms", (System.currentTimeMillis()-t1));
        t1 = System.currentTimeMillis();

        Siri siri = siriObjectFactory.createGMServiceDelivery(values);
        siri.getServiceDelivery().setMoreData(isMoreData);
        logger.info("Creating SIRI-delivery: {} ms", (System.currentTimeMillis()-t1));

        if (isAdHocRequest) {
            logger.info("Returning {}, no requestorRef is set", sizeLimitedIds.size());
        } else {


            MessageRefStructure msgRef = new MessageRefStructure();
            msgRef.setValue(requestorId);
            siri.getServiceDelivery().setRequestMessageRef(msgRef);


            if (requestedIds.size() > generalMessages.size()) {
                //Remove outdated ids
                requestedIds.removeIf(id -> !generalMessages.containsKey(id));
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

        Predicate<SiriObjectStorageKey, GeneralMessage> predicate = SiriObjectStorageKeyUtil.getGeneralMessagePredicate(datasetId,requestedChannels);
        Set<SiriObjectStorageKey> idSet =new HashSet<>(generalMessages.keySet(predicate));

        return idSet;
    }

    public void clearAll() {
        logger.error("Deleting all data - should only be used in test!!!");
        generalMessages.clear();
        changesMap.clear();
        lastUpdateRequested.clear();
        cache.clear();
    }
}
