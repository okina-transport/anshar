package no.rutebanken.anshar.routes.siri.handlers.inbound;

import no.rutebanken.anshar.data.EstimatedTimetables;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.EstimatedTimetableDeliveryStructure;
import uk.org.siri.siri20.EstimatedVehicleJourney;
import uk.org.siri.siri20.Siri;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getOriginalId;
@Service
public class EstimatedTimetableInbound {

    private static final Logger logger = LoggerFactory.getLogger(EstimatedTimetableInbound.class);

    @Autowired
    private EstimatedTimetables estimatedTimetables;

    @Autowired
    private Utils utils;

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private SubscriptionManager subscriptionManager;

    public boolean ingestEstimatedTimetableFromApi(SiriDataType dataFormat, String dataSetId, Siri incoming, List<SubscriptionSetup> subscriptionSetupList) {
        List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = incoming.getServiceDelivery().getEstimatedTimetableDeliveries();
        logger.info("Got ET-delivery: Subscription {}", subscriptionSetupList);

        List<EstimatedVehicleJourney> addedOrUpdated = new ArrayList<>();
        if (estimatedTimetableDeliveries != null) {
            estimatedTimetableDeliveries.forEach(et -> {
                        if (et != null) {
                            if (et.isStatus() != null && !et.isStatus()) {
                                logger.info(utils.getErrorContents(et.getErrorCondition()));
                            } else {
                                if (et.getEstimatedJourneyVersionFrames() != null) {
                                    et.getEstimatedJourneyVersionFrames().forEach(versionFrame -> {
                                        if (versionFrame != null && versionFrame.getEstimatedVehicleJourneies() != null) {
                                            addedOrUpdated.addAll(
                                                    estimatedTimetables.addAll(dataSetId, versionFrame.getEstimatedVehicleJourneies())
                                            );
                                        }
                                    });
                                }
                            }
                        }
                    }
            );
        }

        serverSubscriptionManager.pushUpdatesAsync(dataFormat, addedOrUpdated, dataSetId);

        for (SubscriptionSetup subscriptionSetup : subscriptionSetupList) {
            List<EstimatedVehicleJourney> addedOrUpdatedBySubscription = addedOrUpdated
                    .stream()
                    .filter(estimatedVehicleJourney -> estimatedVehicleJourney.getLineRef().getValue().equals(subscriptionSetup.getLineRefValue()))
                    .collect(Collectors.toList());
            subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdatedBySubscription.size());
//                        logger.info("Active ET-elements: {}, current delivery: {}, {}", estimatedTimetables.getSize(), addedOrUpdatedBySubscription.size(), subscriptionSetup);
        }
        return !addedOrUpdated.isEmpty();
    }
    public Map<String, List<EstimatedVehicleJourney>> splitEstimatedTimetablesByCodespace(List<EstimatedVehicleJourney> estimatedVehicleJourneys) {
        Map<String, List<EstimatedVehicleJourney>> result = new HashMap<>();
        for (EstimatedVehicleJourney etElement : estimatedVehicleJourneys) {
            if (etElement.getDataSource() != null) {

                final String dataSource = etElement.getDataSource();
                if (dataSource != null) {

                    final String codespace = getOriginalId(dataSource);
                    //Override mapped value if present
                    etElement.setDataSource(codespace);

                    final List<EstimatedVehicleJourney> etJourneys = result.getOrDefault(
                            codespace,
                            new ArrayList<>()
                    );

                    etJourneys.add(etElement);
                    result.put(codespace, etJourneys);
                }
            }
        }
        return result;
    }

    public Collection<EstimatedVehicleJourney> ingestEstimatedTimeTables(String datasetId, List<EstimatedVehicleJourney> incomingEstimatedTimeTables) {
        Collection<EstimatedVehicleJourney> result = estimatedTimetables.addAll(datasetId, incomingEstimatedTimeTables);
        if (result.size() > 0) {
            serverSubscriptionManager.pushUpdatesAsync(SiriDataType.ESTIMATED_TIMETABLE, incomingEstimatedTimeTables, datasetId);
        }
        return result;
    }

    public boolean ingestEstimatedTimetable(SubscriptionSetup subscriptionSetup, Siri incoming) {
        List<EstimatedTimetableDeliveryStructure> estimatedTimetableDeliveries = incoming.getServiceDelivery().getEstimatedTimetableDeliveries();
        logger.info("Got ET-delivery: Subscription {}", subscriptionSetup);

        List<EstimatedVehicleJourney> addedOrUpdated = new ArrayList<>();
        if (estimatedTimetableDeliveries != null) {
            estimatedTimetableDeliveries.forEach(et -> {
                        if (et != null) {
                            if (et.isStatus() != null && !et.isStatus()) {
                                logger.info(utils.getErrorContents(et.getErrorCondition()));
                            } else {
                                if (et.getEstimatedJourneyVersionFrames() != null) {
                                    et.getEstimatedJourneyVersionFrames().forEach(versionFrame -> {
                                        if (versionFrame != null && versionFrame.getEstimatedVehicleJourneies() != null) {
                                            if (subscriptionSetup.isUseProvidedCodespaceId()) {
                                                Map<String, List<EstimatedVehicleJourney>> journeysByCodespace = splitEstimatedTimetablesByCodespace(versionFrame.getEstimatedVehicleJourneies());
                                                for (String codespace : journeysByCodespace.keySet()) {

                                                    // List containing added situations for current codespace
                                                    List<EstimatedVehicleJourney> addedJourneys = new ArrayList();

                                                    addedJourneys.addAll(estimatedTimetables.addAll(
                                                            codespace,
                                                            journeysByCodespace.get(codespace)
                                                    ));

                                                    // Push updates to subscribers on this codespace
                                                    serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedJourneys, codespace);

                                                    // Add to complete list of added situations
                                                    addedOrUpdated.addAll(addedJourneys);

                                                }

                                            } else {
                                                addedOrUpdated.addAll(ingestEstimatedTimeTables(subscriptionSetup.getDatasetId(), versionFrame.getEstimatedVehicleJourneies()));
                                            }
                                        }
                                    });
                                }
                            }
                        }
                    }
            );
        }


        serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedOrUpdated, subscriptionSetup.getDatasetId());

        subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());

        logger.info("Active ET-elements: {}, current delivery: {}, {}", estimatedTimetables.getSize(), addedOrUpdated.size(), subscriptionSetup);

        return !addedOrUpdated.isEmpty();
    }
}
