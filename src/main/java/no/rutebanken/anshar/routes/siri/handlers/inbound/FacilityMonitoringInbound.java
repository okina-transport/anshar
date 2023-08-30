package no.rutebanken.anshar.routes.siri.handlers.inbound;

import no.rutebanken.anshar.data.FacilityMonitoring;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.FacilityConditionStructure;
import uk.org.siri.siri20.FacilityMonitoringDeliveryStructure;
import uk.org.siri.siri20.GeneralMessage;
import uk.org.siri.siri20.GeneralMessageDeliveryStructure;
import uk.org.siri.siri20.OrganisationRefStructure;
import uk.org.siri.siri20.Siri;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getOriginalId;
@Service
public class FacilityMonitoringInbound {

    private static final Logger logger = LoggerFactory.getLogger(FacilityMonitoringInbound.class);

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private FacilityMonitoring facilityMonitoring;

    @Autowired
    private Utils utils;

    @Autowired
    private SubscriptionManager subscriptionManager;

    public Map<String, List<FacilityConditionStructure>> splitFacilitiesByCodespace(List<FacilityConditionStructure> facilitiesConditions) {
        Map<String, List<FacilityConditionStructure>> result = new HashMap<>();
        for (FacilityConditionStructure facilityCondition : facilitiesConditions) {
            final OrganisationRefStructure ownerRef = facilityCondition.getFacility() != null ? facilityCondition.getFacility().getOwnerRef() : null;
            if (ownerRef != null) {
                final String codespace = getOriginalId(ownerRef.getValue());

                //Override mapped value if present
                ownerRef.setValue(codespace);

                final List<FacilityConditionStructure> facilities = result.getOrDefault(
                        codespace,
                        new ArrayList<>()
                );

                facilities.add(facilityCondition);
                result.put(codespace, facilities);
            }
        }
        return result;
    }

    public Collection<FacilityConditionStructure> ingestFacilities(String datasetId, List<FacilityConditionStructure> incomingFacilities) {
        Collection<FacilityConditionStructure> result = facilityMonitoring.addAll(datasetId, incomingFacilities);
        if (!result.isEmpty()) {
            serverSubscriptionManager.pushUpdatesAsync(SiriDataType.FACILITY_MONITORING, incomingFacilities, datasetId);
        }
        return result;
    }

    public boolean ingestFacility(SubscriptionSetup subscriptionSetup, Siri incoming) {
        List<FacilityMonitoringDeliveryStructure> facilityMonitoringStructures = incoming.getServiceDelivery().getFacilityMonitoringDeliveries();
        logger.debug("Got FM-delivery: Subscription [{}] ", subscriptionSetup);

        List<FacilityConditionStructure> addedOrUpdated = new ArrayList<>();

        for (FacilityMonitoringDeliveryStructure facilityMonitoringDeliveryStructure : facilityMonitoringStructures) {
            if (facilityMonitoringDeliveryStructure.isStatus() != null && !facilityMonitoringDeliveryStructure.isStatus()) {
                logger.info(utils.getErrorContents(facilityMonitoringDeliveryStructure.getErrorCondition()));
            } else {
                if (facilityMonitoringDeliveryStructure.getFacilityConditions() != null) {
                    if (subscriptionSetup.isUseProvidedCodespaceId()) {
                        Map<String, List<FacilityConditionStructure>> situationsByCodespace = splitFacilitiesByCodespace(facilityMonitoringDeliveryStructure.getFacilityConditions());
                        for (String codespace : situationsByCodespace.keySet()) {
                            // List containing added situations for current codespace
                            List<FacilityConditionStructure> addedFacilities = new ArrayList();

                            Collection<FacilityConditionStructure> ingested = ingestFacilities(codespace, situationsByCodespace.get(codespace));
                            addedFacilities.addAll(ingested);

                            // Push updates to subscribers on this codespace
                            serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedFacilities, codespace);

                            // Add to complete list of added situations
                            addedOrUpdated.addAll(addedFacilities);
                        }
                    } else {
                        Collection<FacilityConditionStructure> ingested = ingestFacilities(subscriptionSetup.getDatasetId(), facilityMonitoringDeliveryStructure.getFacilityConditions());
                        addedOrUpdated.addAll(ingested);
                        serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedOrUpdated, subscriptionSetup.getDatasetId());
                    }
                }
            }
        }

        serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedOrUpdated, subscriptionSetup.getDatasetId());
        subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());
        logger.debug("Active FM-elements: {}, current delivery: {}, {}", facilityMonitoring.getSize(), addedOrUpdated.size(), subscriptionSetup);

        return !addedOrUpdated.isEmpty();
    }

    public boolean ingestFacilityFromApi(SiriDataType dataFormat, String datasetId, Siri incoming, List<SubscriptionSetup> subscriptionSetupList) {
        boolean deliveryContainsData;
        List<FacilityMonitoringDeliveryStructure> facilityMonitoringDeliveries = incoming.getServiceDelivery().getFacilityMonitoringDeliveries();
        logger.info("Got FM-delivery: Subscription [{}]", subscriptionSetupList);

        List<FacilityConditionStructure> addedOrUpdated = new ArrayList<>();
        if (facilityMonitoringDeliveries != null) {
            facilityMonitoringDeliveries.forEach(fm -> {
                        if (fm != null) {
                            if (fm.isStatus() != null && !fm.isStatus()) {
                                logger.info(utils.getErrorContents(fm.getErrorCondition()));
                            } else {
                                if (fm.getFacilityConditions() != null && fm.getFacilityConditions() != null) {
                                    addedOrUpdated.addAll(facilityMonitoring.addAll(datasetId, fm.getFacilityConditions()));
                                }
                            }
                        }
                    }
            );
        }

        serverSubscriptionManager.pushUpdatesAsync(dataFormat, addedOrUpdated, datasetId);

        deliveryContainsData = !addedOrUpdated.isEmpty();

        for (SubscriptionSetup subscriptionSetup : subscriptionSetupList) {
            subscriptionManager.incrementObjectCounter(subscriptionSetup, 1);
//                        logger.info("Active FM-elements: {}, current delivery: {}, {}", situations.getSize(), addedOrUpdated.size(), subscriptionSetup);
        }
        return deliveryContainsData;
    }
}
