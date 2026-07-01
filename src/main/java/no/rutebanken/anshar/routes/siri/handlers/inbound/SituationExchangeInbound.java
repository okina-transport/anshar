package no.rutebanken.anshar.routes.siri.handlers.inbound;

import com.hazelcast.scheduledexecutor.IScheduledExecutorService;
import com.hazelcast.scheduledexecutor.IScheduledFuture;
import no.rutebanken.anshar.data.GeneralMessages;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.data.collections.ExtendedHazelcastService;
import no.rutebanken.anshar.data.util.GeneralMessageMapper;
import no.rutebanken.anshar.routes.kafka.KafkaConfig;
import no.rutebanken.anshar.routes.kafka.KafkaRouteBuilder;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getOriginalId;
import static no.rutebanken.anshar.routes.validation.validators.Constants.DATASET_ID_HEADER_NAME;

@Service
@EnableScheduling
public class SituationExchangeInbound {

    private static final Logger logger = LoggerFactory.getLogger(SituationExchangeInbound.class);


    private final Utils utils;
    private final ServerSubscriptionManager serverSubscriptionManager;
    private final SubscriptionManager subscriptionManager;
    private final Situations situations;
    private final GeneralMessageInbound generalMessageInbound;
    private final SuperIdReversionProcess reversionIdProcess;
    private final GeneralMessageMapper gmMapper;
    private final ExtendedHazelcastService hazelcastService;
    private KafkaConfig kafkaConfig;
    private final IScheduledExecutorService sharedScheduler;

    @Produce(KafkaRouteBuilder.SEND_SX_IN_TO_KAFKA)
    protected ProducerTemplate sendSxInToKafka;

    public SituationExchangeInbound(Utils utils, ServerSubscriptionManager serverSubscriptionManager, SubscriptionManager subscriptionManager, Situations situations, GeneralMessages generalMessages, GeneralMessageInbound generalMessageInbound,
                                    SuperIdReversionProcess reversionIdProcess, GeneralMessageMapper gmMapper, KafkaConfig kafkaConfig, @Qualifier("getSharedScheduler") IScheduledExecutorService sharedScheduler,
                                    ExtendedHazelcastService hazelcastService) {
        this.utils = utils;
        this.serverSubscriptionManager = serverSubscriptionManager;
        this.subscriptionManager = subscriptionManager;
        this.situations = situations;
        this.generalMessageInbound = generalMessageInbound;
        this.reversionIdProcess = reversionIdProcess;
        this.gmMapper = gmMapper;
        this.kafkaConfig = kafkaConfig;
        this.sharedScheduler = sharedScheduler;
        this.hazelcastService = hazelcastService;
    }

    public boolean ingestSituationExchangeFromApi(SiriDataType dataFormat, String dataSetId, Siri incoming, List<SubscriptionSetup> subscriptionSetupList, Long inboundTime) {
        boolean deliveryContainsData;
        List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = incoming.getServiceDelivery().getSituationExchangeDeliveries();
        logger.info("Got SX-delivery: Subscription [{}]", subscriptionSetupList);

        List<PtSituationElement> addedOrUpdated = new ArrayList<>();
        if (situationExchangeDeliveries != null) {
            situationExchangeDeliveries.forEach(sx -> {
                        if (sx != null) {
                            if (sx.isStatus() != null && !sx.isStatus()) {
                                logger.info(utils.getErrorContents(sx.getErrorCondition()));
                            } else {
                                if (sx.getSituations() != null && sx.getSituations().getPtSituationElements() != null) {
                                    Collection<PtSituationElement> ingested = ingestSituations(dataSetId, sx.getSituations().getPtSituationElements(), false, inboundTime);
                                    addedOrUpdated.addAll(ingested);
                                    serverSubscriptionManager.pushUpdatesAsync(dataFormat, addedOrUpdated, dataSetId, inboundTime);
                                }
                            }
                        }
                    }
            );
        }
        deliveryContainsData = !addedOrUpdated.isEmpty();

        for (SubscriptionSetup subscriptionSetup : subscriptionSetupList) {
            subscriptionManager.incrementObjectCounter(subscriptionSetup, 1);
//                        logger.info("Active SX-elements: {}, current delivery: {}, {}", situations.getSize(), addedOrUpdated.size(), subscriptionSetup);
        }
        return deliveryContainsData;
    }

    public boolean ingestSituationExchange(SubscriptionSetup subscriptionSetup, Siri incoming, Long inboundTime) {
        List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = incoming.getServiceDelivery().getSituationExchangeDeliveries();
        logger.info("Got SX-delivery: Subscription [{}]", subscriptionSetup);

        if (kafkaConfig.isKafkaEnabled() && kafkaConfig.isSendSiriSxInToKafka()) {
            sendSxInToKafka.asyncRequestBodyAndHeader(sendSxInToKafka.getDefaultEndpoint(), incoming, DATASET_ID_HEADER_NAME, subscriptionSetup.getDatasetId());
        }

        if (subscriptionSetup.getRevertIds() != null && subscriptionSetup.getRevertIds()) {
            incoming = reversionIdProcess.revertIds(incoming, subscriptionSetup.getDatasetId());
        }

        List<PtSituationElement> addedOrUpdated = new ArrayList<>();
        if (situationExchangeDeliveries != null) {
            situationExchangeDeliveries.forEach(sx -> {
                        if (sx != null) {
                            if (sx.isStatus() != null && !sx.isStatus()) {
                                logger.info(utils.getErrorContents(sx.getErrorCondition()));
                            } else {
                                if (sx.getSituations() != null && sx.getSituations().getPtSituationElements() != null) {
                                    setValidityPeriodAndStartTimeIfNull(sx.getSituations().getPtSituationElements(), subscriptionSetup.getDatasetId());

                                    if (subscriptionSetup.isUseProvidedCodespaceId()) {
                                        Map<String, List<PtSituationElement>> situationsByCodespace = splitSituationsByCodespace(sx.getSituations().getPtSituationElements());
                                        for (String codespace : situationsByCodespace.keySet()) {

                                            // List containing added situations for current codespace
                                            List<PtSituationElement> addedSituations = new ArrayList();

                                            Collection<PtSituationElement> ingested = ingestSituations(codespace, situationsByCodespace.get(codespace), false, inboundTime);
                                            addedSituations.addAll(ingested);

                                            // Push updates to subscribers on this codespace
                                            serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedSituations, codespace, inboundTime);

                                            // Add to complete list of added situations
                                            addedOrUpdated.addAll(addedSituations);

                                        }

                                    } else {
                                        Collection<PtSituationElement> ingested = ingestSituations(subscriptionSetup.getDatasetId(), sx.getSituations().getPtSituationElements(), false, inboundTime);
                                        addedOrUpdated.addAll(ingested);
                                        serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedOrUpdated, subscriptionSetup.getDatasetId(), inboundTime);
                                    }
                                }
                            }
                        }
                    }
            );
        }

        subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());

        logger.info("Active SX-elements: {}, current delivery: {}, {}", situations.getSize(), addedOrUpdated.size(), subscriptionSetup);

        return !addedOrUpdated.isEmpty();
    }

    public void closeMissingAlerts(String datasetId, List<PtSituationElement> currentOpenedSituations) {
        closeMissingAlerts(datasetId, currentOpenedSituations, null);
    }

    /**
     * Close situations that are no more existing, in the the GTFSRT-api
     *
     * @param datasetId               the datasetId for which situations must be closed
     * @param currentOpenedSituations situations that are currently available in the flow
     */
    public void closeMissingAlerts(String datasetId, List<PtSituationElement> currentOpenedSituations, Long inboundTime) {

        Collection<PtSituationElement> storedSituations = situations.getAll(datasetId);
        if (storedSituations == null || storedSituations.isEmpty()) {
            return;
        }

        List<PtSituationElement> situationsToClose = new ArrayList<>();
        for (PtSituationElement storedSituation : storedSituations) {
            String situationNumber = storedSituation.getSituationNumber().getValue();
            if (isSituationInList(situationNumber, currentOpenedSituations)) {
                // situation is still existing in the flow, no need to close it
                continue;
            }

            storedSituation.setProgress(WorkflowStatusEnumeration.CLOSED);

            situationsToClose.add(storedSituation);
        }

        if (!situationsToClose.isEmpty()) {
            ingestSituations(datasetId, situationsToClose, true, inboundTime);
        }

    }

    private boolean isSituationInList(String situationNumber, List<PtSituationElement> situationList) {
        return situationList.stream().anyMatch(situation -> situation.getSituationNumber() != null && situation.getSituationNumber().getValue().equals(situationNumber));
    }

    public Map<String, List<PtSituationElement>> splitSituationsByCodespace(List<PtSituationElement> ptSituationElements) {
        Map<String, List<PtSituationElement>> result = new HashMap<>();
        for (PtSituationElement ptSituationElement : ptSituationElements) {
            final RequestorRef participantRef = ptSituationElement.getParticipantRef();
            if (participantRef != null) {
                final String codespace = getOriginalId(participantRef.getValue());

                //Override mapped value if present
                participantRef.setValue(codespace);

                final List<PtSituationElement> situations = result.getOrDefault(
                        codespace,
                        new ArrayList<>()
                );

                situations.add(ptSituationElement);
                result.put(codespace, situations);
            }
        }
        return result;
    }


    public Collection<PtSituationElement> ingestSituations(String datasetId, List<PtSituationElement> incomingSituations, boolean publishToOutbound) {
        return ingestSituations(datasetId, incomingSituations, publishToOutbound, null);
    }

    /**
     * Ingest incoming situation into cache
     *
     * @param datasetId          dataset of the subscription
     * @param incomingSituations incoming situations to ingest
     * @param publishToOutbound  if publication should be published or not to the outboud subcriptions
     * @return
     */
    public Collection<PtSituationElement> ingestSituations(String datasetId, List<PtSituationElement> incomingSituations, boolean publishToOutbound, Long inboundTime) {
        Collection<PtSituationElement> result = situations.addAll(datasetId, incomingSituations);
        if (publishToOutbound && CollectionUtils.isNotEmpty(result)) {
            serverSubscriptionManager.pushUpdatesAsync(SiriDataType.SITUATION_EXCHANGE, new ArrayList<>(result), datasetId, inboundTime);
        }

        List<PtSituationElement> currentlyOpenedSituations = incomingSituations.stream()
                .filter(this::shouldSituationBeDisplayed)
                .toList();

        if (!currentlyOpenedSituations.isEmpty()) {
            convertToGeneralMessageAndIngest(datasetId, currentlyOpenedSituations, inboundTime);
        }

        scheduleFutureMessages(datasetId, incomingSituations, null);
        return result;
    }


    /**
     * Search situation with publication windows in the future and schedule them to enter GM cache at the begining of the publication window
     *
     * @param incomingSituations situations to check and eventually schedule in the future.
     */
    private void scheduleFutureMessages(String datasetId, List<PtSituationElement> incomingSituations, Long inboundTime) {

        // Situations with no publicationWindow MUST NOT be converted to GM
        List<PtSituationElement> situationsToSchedule = incomingSituations.stream()
                .filter(sit -> !shouldSituationBeDisplayed(sit) && CollectionUtils.isNotEmpty(sit.getPublicationWindows()))
                .toList();

        for (PtSituationElement situationToSchedule : situationsToSchedule) {
            LocalDateTime scheduledDate = getSituationStartPublicationTime(situationToSchedule);
            String situationNumber = situationToSchedule.getSituationNumber().getValue();
            if (hazelcastService.getScheduledGeneralMessages(datasetId).containsKey(situationNumber)) {
                LocalDateTime previousTime = hazelcastService.getScheduledGeneralMessages(datasetId).get(situationNumber).getLeft();
                if (previousTime.isEqual(scheduledDate)) {
                    // GM ingestion already planned. No changes in planned time. No modification needed
                    continue;
                } else {
                    // planned time has changed. Need to cancel previous task and re-create a new one
                    cancelTask(datasetId, situationNumber);
                    hazelcastService.getScheduledGeneralMessages(datasetId).remove(situationNumber);
                }
            }


            long delay = Duration.between(LocalDateTime.now(), scheduledDate).toMillis();

            List<PtSituationElement> futureSituations = new ArrayList<>();
            futureSituations.add(situationToSchedule);
            IScheduledFuture<Object> plannedTask = sharedScheduler.schedule(() -> {
                logger.info("GeneralMessage - launching future gm conversion for situation: {} ", situationNumber);
                convertToGeneralMessageAndIngest(datasetId, futureSituations, inboundTime);

            }, delay, TimeUnit.MILLISECONDS);
            logger.info("SCHEDULE - situationNumber={} -> taskName={}", situationNumber, plannedTask.getHandler().getTaskName());

            logger.info("GeneralMessage : scheduling future gm conversion for situation:{} - scheduledDate:{} ", situationNumber, scheduledDate);
            hazelcastService.getScheduledGeneralMessages(datasetId).put(situationNumber, Pair.of(scheduledDate, plannedTask.getHandler().getTaskName()));
        }

    }

    @Scheduled(fixedRate = 600000)
    public void cleanupFinishedTasks() {
        sharedScheduler.getAllScheduledFutures().values().forEach(list ->
                list.forEach(future -> {
                    if (future.isDone() || future.isCancelled()) {
                        try {
                            future.dispose();
                        } catch (Exception e) {
                            logger.debug("GeneralMessage : impossible de disposer une tâche terminée - {}", e.getMessage());
                        }
                    }
                })
        );
    }

    private IScheduledFuture<Object> findScheduledFutureByName(String taskName) {
        for (List<IScheduledFuture<Object>> list : sharedScheduler.getAllScheduledFutures().values()) {
            for (IScheduledFuture<Object> handler : list) {
                if (taskName.equals(handler.getHandler().getTaskName())) {
                    return handler;
                }
            }
        }
        return null;
    }


    private void cancelTask(String datasetId, String situationNumber) {
        String taskName = hazelcastService.getScheduledGeneralMessages(datasetId).get(situationNumber).getRight();
        logger.info("cancelTask - taskName recherché = '{}'", taskName);
        IScheduledFuture<Object> future = findScheduledFutureByName(taskName);
        logger.info("cancelTask - future trouvée = {}", future != null);
        if (future != null) {
            future.cancel(false);
            future.dispose();
        }
    }

    private LocalDateTime getSituationStartPublicationTime(PtSituationElement situationToSchedule) {

        Optional<ZonedDateTime> lowestOpt = situationToSchedule.getPublicationWindows().stream()
                .map(HalfOpenTimestampOutputRangeStructure::getStartTime)
                .min(Comparator.naturalOrder());

        if (lowestOpt.isEmpty()) {
            throw new IllegalStateException("Unable to find valid start time for situation:" + situationToSchedule.getSituationNumber());
        }

        return lowestOpt.get().toLocalDateTime();
    }


    /**
     * Indicates if a situation should be displayed or not
     * true : should be displayed, at least one publication window has null or passed startDate
     * false : should not be displayed, all publication windows are in the future
     *
     * @param situation situation to check
     * @return
     */
    private boolean shouldSituationBeDisplayed(PtSituationElement situation) {
        if (situation.getPublicationWindows().isEmpty()) {
            return false;
        }
        ZonedDateTime now = ZonedDateTime.now();
        return situation.getPublicationWindows().stream()
                .anyMatch(pubWindow -> pubWindow.getStartTime() == null || pubWindow.getStartTime().isBefore(now));
    }

    /**
     * Convert a list of situations to a list of generalMessages and ingest them
     *
     * @param datasetId          the dataset on which the general messages must be ingested
     * @param incomingSituations the situations that must be converted to GeneralMessages and must be ingested
     */
    private void convertToGeneralMessageAndIngest(String datasetId, List<PtSituationElement> incomingSituations, Long inboundTime) {

        incomingSituations = filterUnmappableAffects(incomingSituations);
        // Open perturbations
        List<GeneralMessage> incomingMessages = incomingSituations.stream()
                .filter(situation -> situation.getProgress() == null || !WorkflowStatusEnumeration.CLOSED.equals(situation.getProgress()))
                .map(situation -> gmMapper.mapToGeneralMessage(datasetId, situation))
                .collect(Collectors.toList());

        generalMessageInbound.ingestGeneralMessages(datasetId, incomingMessages, true, inboundTime);


        // Closed perturbations that need to be removed from cache
        List<GeneralMessageCancellation> cancellations = incomingSituations.stream()
                .filter(situation -> WorkflowStatusEnumeration.CLOSED.equals(situation.getProgress()))
                .map(situation -> gmMapper.mapToCancellations(situation))
                .toList();

        if (CollectionUtils.isNotEmpty(cancellations)) {
            generalMessageInbound.ingestGeneralMessagesCancellations(datasetId, cancellations, true, inboundTime);
        }


    }

    private List<PtSituationElement> filterUnmappableAffects(List<PtSituationElement> incomingSituations) {
        List<PtSituationElement> filteredAffects = new ArrayList<>();
        for (PtSituationElement incomingSituation : incomingSituations) {
            if (incomingSituation.getAffects() == null || incomingSituation.getAffects().getNetworks() != null || incomingSituation.getAffects().getStopPlaces() != null || incomingSituation.getAffects().getStopPoints() != null) {
                filteredAffects.add(incomingSituation);
            }
        }

        return filteredAffects;
    }

    public void setValidityPeriodAndStartTimeIfNull(List<PtSituationElement> situationExchangeDeliveries, String datasetId) {
        for (PtSituationElement situationElement : situationExchangeDeliveries) {
            ZoneId zoneId = ZoneId.systemDefault();
            for (HalfOpenTimestampOutputRangeStructure validityPeriod : situationElement.getValidityPeriods()) {
                if (validityPeriod.getStartTime() == null) {
                    ZonedDateTime timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.MIN_VALUE), zoneId);
                    validityPeriod.setStartTime(timestamp);
                    logger.info("PtSituationElement without start time and/or validity period for datasetId : " + datasetId +
                            " with situation element id : " + situationElement.getSituationNumber().getValue());
                }
            }
            if (situationElement.getValidityPeriods().isEmpty()) {
                HalfOpenTimestampOutputRangeStructure validityPeriod = new HalfOpenTimestampOutputRangeStructure();
                ZonedDateTime timestamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.MIN_VALUE), zoneId);
                validityPeriod.setStartTime(timestamp);
                situationElement.getValidityPeriods().add(validityPeriod);
                logger.info("PtSituationElement without start time and/or validity period for datasetId : " + datasetId +
                        " with situation element id : " + situationElement.getSituationNumber().getValue());
            }
        }
    }

    public void removeSituation(String datasetId, PtSituationElement situation) {
        situations.removeSituation(datasetId, situation);
    }
}
