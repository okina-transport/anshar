package no.rutebanken.anshar.routes.siri.handlers.inbound;

import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.Utils;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import no.rutebanken.anshar.subscription.SubscriptionSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.VehicleActivityStructure;
import uk.org.siri.siri20.VehicleMonitoringDeliveryStructure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static no.rutebanken.anshar.routes.siri.transformer.impl.OutboundIdAdapter.getOriginalId;
@Service
public class VehicleMonitoringInbound {

    private static final Logger logger = LoggerFactory.getLogger(VehicleMonitoringInbound.class);

    @Autowired
    private Utils utils;

    @Autowired
    private VehicleActivities vehicleActivities;

    @Autowired
    private ServerSubscriptionManager serverSubscriptionManager;

    @Autowired
    private SubscriptionManager subscriptionManager;

    public boolean ingestVehicleMonitoringFromApi(SiriDataType dataFormat, String dataSetId, Siri incoming, List<SubscriptionSetup> subscriptionSetupList) {
        logger.debug("Got VM-delivery: Subscription [{}] {}", subscriptionSetupList);

        List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = incoming.getServiceDelivery().getVehicleMonitoringDeliveries();
        List<VehicleActivityStructure> addedOrUpdated = new ArrayList<>();
        if (vehicleMonitoringDeliveries != null) {
            vehicleMonitoringDeliveries.forEach(vm -> {
                        if (vm != null) {
                            if (vm.isStatus() != null && !vm.isStatus() || vm.getErrorCondition() != null) {
                                logger.info(utils.getErrorContents(vm.getErrorCondition()));
                            } else {
                                if (vm.getVehicleActivities() != null) {
                                    addedOrUpdated.addAll(
                                            vehicleActivities.addAll(dataSetId, vm.getVehicleActivities()));
                                }
                            }
                        }
                    }
            );
        }

        serverSubscriptionManager.pushUpdatesAsync(dataFormat, addedOrUpdated, dataSetId);

        for (SubscriptionSetup subscriptionSetup : subscriptionSetupList) {
            List<VehicleActivityStructure> addedOrUpdatedBySubscription = addedOrUpdated
                    .stream()
                    .filter(vehicleActivityStructure -> vehicleActivityStructure.getVehicleMonitoringRef().getValue().equals(subscriptionSetup.getVehicleMonitoringRefValue()))
                    .collect(Collectors.toList());
            subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdatedBySubscription.size());
        }
        return !addedOrUpdated.isEmpty();
    }

    public Collection<VehicleActivityStructure> ingestVehicleActivities(String datasetId, List<VehicleActivityStructure> incomingVehicleActivities) {
        Collection<VehicleActivityStructure> result = vehicleActivities.addAll(datasetId, incomingVehicleActivities);
        if (result.size() > 0) {
            serverSubscriptionManager.pushUpdatesAsync(SiriDataType.VEHICLE_MONITORING, incomingVehicleActivities, datasetId);
        }
        return result;
    }
    public String getVehicleRefs(Siri incomingData) {
        List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = incomingData.getServiceDelivery().getVehicleMonitoringDeliveries();
        List<String> vehicleRefs = new ArrayList<>();

        for (VehicleMonitoringDeliveryStructure vehicleMonitoringDelivery : vehicleMonitoringDeliveries) {
            for (VehicleActivityStructure vehicleActivity : vehicleMonitoringDelivery.getVehicleActivities()) {
                vehicleRefs.add(vehicleActivity.getVehicleMonitoringRef().getValue());
            }
        }

        return vehicleRefs.stream()
                .distinct()
                .collect(Collectors.joining(","));
    }

    public Map<String, List<VehicleActivityStructure>> splitVehicleMonitoringByCodespace(
            List<VehicleActivityStructure> activityStructures
    ) {
        Map<String, List<VehicleActivityStructure>> result = new HashMap<>();
        for (VehicleActivityStructure vmElement : activityStructures) {
            if (vmElement.getMonitoredVehicleJourney() != null) {

                final String dataSource = vmElement.getMonitoredVehicleJourney().getDataSource();
                if (dataSource != null) {

                    final String codespace = getOriginalId(dataSource);
                    //Override mapped value if present
                    vmElement.getMonitoredVehicleJourney().setDataSource(codespace);

                    final List<VehicleActivityStructure> vehicles = result.getOrDefault(
                            codespace,
                            new ArrayList<>()
                    );

                    vehicles.add(vmElement);
                    result.put(codespace, vehicles);
                }
            }
        }
        return result;
    }

    /**
     * Read incoming data to find the lineRef (to identify which line has received data)
     *
     * @param incomingData incoming message
     * @return A string with all lines found in the incoming message
     */
    public String getLineRef(Siri incomingData) {
        List<VehicleMonitoringDeliveryStructure> vehicleDeliveries = incomingData.getServiceDelivery().getVehicleMonitoringDeliveries();
        List<String> lineRefs = new ArrayList<>();

        for (VehicleMonitoringDeliveryStructure vehicleDelivery : vehicleDeliveries) {
            for (VehicleActivityStructure vehicleActivity : vehicleDelivery.getVehicleActivities()) {
                lineRefs.add(vehicleActivity.getMonitoredVehicleJourney().getLineRef().getValue());
            }
        }
        return lineRefs.stream()
                .distinct()
                .collect(Collectors.joining(","));
    }

    public boolean ingestVehicleMonitoring(SubscriptionSetup subscriptionSetup, Siri incoming) {
        List<VehicleMonitoringDeliveryStructure> vehicleMonitoringDeliveries = incoming.getServiceDelivery().getVehicleMonitoringDeliveries();
        logger.debug("Got VM-delivery: Subscription [{}] {}", subscriptionSetup, subscriptionSetup.forwardPositionData() ? "- Position only" : "");

        List<VehicleActivityStructure> addedOrUpdated = new ArrayList<>();
        if (vehicleMonitoringDeliveries != null) {
            vehicleMonitoringDeliveries.forEach(vm -> {
                        if (vm != null) {
                            if (vm.isStatus() != null && !vm.isStatus()) {
                                logger.info(utils.getErrorContents(vm.getErrorCondition()));
                            } else {
                                if (vm.getVehicleActivities() != null) {
                                    if (subscriptionSetup.isUseProvidedCodespaceId()) {
                                        Map<String, List<VehicleActivityStructure>> vehiclesByCodespace = splitVehicleMonitoringByCodespace(vm.getVehicleActivities());
                                        for (String codespace : vehiclesByCodespace.keySet()) {

                                            // List containing added situations for current codespace
                                            List<VehicleActivityStructure> addedVehicles = new ArrayList();

                                            addedVehicles.addAll(vehicleActivities.addAll(
                                                    codespace,
                                                    vehiclesByCodespace.get(codespace)
                                            ));

                                            // Push updates to subscribers on this codespace
                                            serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedVehicles, codespace);

                                            // Add to complete list of added situations
                                            addedOrUpdated.addAll(addedVehicles);

                                        }

                                    } else {
                                        addedOrUpdated.addAll(ingestVehicleActivities(subscriptionSetup.getDatasetId(), vm.getVehicleActivities()));
                                    }
                                }
                            }
                        }
                    }
            );
        }
        serverSubscriptionManager.pushUpdatesAsync(subscriptionSetup.getSubscriptionType(), addedOrUpdated, subscriptionSetup.getDatasetId());

        subscriptionManager.incrementObjectCounter(subscriptionSetup, addedOrUpdated.size());

        logger.debug("Active VM-elements: {}, current delivery: {}, {}", vehicleActivities.getSize(), addedOrUpdated.size(), subscriptionSetup);

        return !addedOrUpdated.isEmpty();
    }
}
