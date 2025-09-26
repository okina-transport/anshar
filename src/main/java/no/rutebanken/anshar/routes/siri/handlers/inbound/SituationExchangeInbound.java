package no.rutebanken.anshar.routes.siri.handlers.inbound;

import no.rutebanken.anshar.data.GeneralMessages;
import no.rutebanken.anshar.data.Situations;
import no.rutebanken.anshar.data.util.GeneralMessageMapper;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri21.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getOriginalId;

@Service
public class SituationExchangeInbound {

    private static final Logger logger = LoggerFactory.getLogger(SituationExchangeInbound.class);

    @Autowired
    private Utils utils;

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @Autowired
    private Situations situations;

    @Autowired
    private GeneralMessages generalMessages;

    @Autowired
    private SuperIdReversionProcess reversionIdProcess;

    public boolean ingestSituationExchangeFromApi(SiriDataType dataFormat, String dataSetId, Siri incoming, List<SubscriptionSetup> subscriptionSetupList) {
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
                                    Collection<PtSituationElement> ingested = ingestSituations(dataSetId, sx.getSituations().getPtSituationElements(), false);
                                    addedOrUpdated.addAll(ingested);
                                    serverSubscriptionManager.pushUpdatesAsync(dataFormat, addedOrUpdated, dataSetId);
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

    public boolean ingestSituationExchange(SubscriptionSetup subscriptionSetup, Siri incoming) {
        List<SituationExchangeDeliveryStructure> situationExchangeDeliveries = incoming.getServiceDelivery().getSituationExchangeDeliveries();
        logger.info("Got SX-delivery: Subscription [{}]", subscriptionSetup);

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

                                            Collection<PtSituationElement> ingested = ingestSituations(codespace, situationsByCodespace.get(codespace), false);
                                            addedSituations.addAll(ingested);

                                            // Push updates to subscribers on this codespace
                                            serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedSituations, codespace);

                                            // Add to complete list of added situations
                                            addedOrUpdated.addAll(addedSituations);

                                        }

                                    } else {
                                        Collection<PtSituationElement> ingested = ingestSituations(subscriptionSetup.getDatasetId(), sx.getSituations().getPtSituationElements(), false);
                                        addedOrUpdated.addAll(ingested);
                                        serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedOrUpdated, subscriptionSetup.getDatasetId());
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

    /**
     * Close situations that are no more existing, in the the GTFSRT-api
     *
     * @param datasetId               the datasetId for which situations must be closed
     * @param currentOpenedSituations situations that are currently available in the flow
     */
    public void closeMissingAlerts(String datasetId, List<PtSituationElement> currentOpenedSituations) {

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
            ingestSituations(datasetId, situationsToClose, true);
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


    /**
     * Ingest incoming situation into cache
     *
     * @param datasetId          dataset of the subscription
     * @param incomingSituations incoming situations to ingest
     * @param publishToOutbound  if publication should be published or not to the outboud subcriptions
     * @return
     */
    public Collection<PtSituationElement> ingestSituations(String datasetId, List<PtSituationElement> incomingSituations, boolean publishToOutbound) {
        Collection<PtSituationElement> result = situations.addAll(datasetId, incomingSituations);
        if (publishToOutbound && CollectionUtils.isNotEmpty(result)) {
            serverSubscriptionManager.pushUpdatesAsync(SiriDataType.SITUATION_EXCHANGE, new ArrayList<>(result), datasetId);
        }

        convertToGeneralMessageAndIngest(datasetId, incomingSituations);
        return result;
    }

    /**
     * Convert a list of situations to a list of generalMessages and ingest them
     *
     * @param datasetId          the dataset on which the general messages must be ingested
     * @param incomingSituations the situations that must be converted to GeneralMessages and must be ingested
     */
    private void convertToGeneralMessageAndIngest(String datasetId, List<PtSituationElement> incomingSituations) {

        List<GeneralMessage> incomingMessages = incomingSituations.stream()
                .map(GeneralMessageMapper::mapToGeneralMessage)
                .collect(Collectors.toList());

        Collection<GeneralMessage> added = generalMessages.addAll(datasetId, incomingMessages);

        serverSubscriptionManager.pushUpdatesAsync(SiriDataType.GENERAL_MESSAGE, new ArrayList(added), datasetId);

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
