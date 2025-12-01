package no.rutebanken.anshar.util;


import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.entur.siri.validator.SiriValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.*;

import javax.xml.datatype.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class SiriUtils {

    private static final Logger logger = LoggerFactory.getLogger(SiriUtils.class);


    /**
     * Builds a set of dataset using datasetId header received in the request
     * (datasetId header can contain single dataset (PROV1) or multiple datasets (PROV1,PROV2) )
     *
     * @param datasetHeader header that contains, 0,1 or many datasets
     * @return a set of datasetId
     */
    public static Set<String> generateDatasetListFromHeader(String datasetHeader) {
        Set<String> datasets = new HashSet<>();
        if (datasetHeader != null && datasetHeader.contains(",")) {
            datasets = Arrays.stream(datasetHeader.split(",")).collect(Collectors.toSet());
        } else if (datasetHeader != null) {
            datasets = new HashSet<>(List.of(datasetHeader));
        }
        return datasets;
    }

    public static boolean hasETRequest(SubscriptionRequest subscriptionRequest) {
        return CollectionUtils.isNotEmpty(subscriptionRequest.getEstimatedTimetableSubscriptionRequests());
    }

    public static boolean hasVMRequest(SubscriptionRequest subscriptionRequest) {
        return CollectionUtils.isNotEmpty(subscriptionRequest.getVehicleMonitoringSubscriptionRequests());
    }

    public static boolean hasSMRequest(SubscriptionRequest subscriptionRequest) {
        return CollectionUtils.isNotEmpty(subscriptionRequest.getStopMonitoringSubscriptionRequests());
    }

    public static boolean hasFMRequest(SubscriptionRequest subscriptionRequest) {
        return CollectionUtils.isNotEmpty(subscriptionRequest.getFacilityMonitoringSubscriptionRequests());
    }

    public static SiriValidator.Version getVersionEnum(String version) {
        if ("1.0".equals(version)) {
            return SiriValidator.Version.VERSION_1_0;
        } else if ("1.3".equals(version)) {
            return SiriValidator.Version.VERSION_1_3;
        } else if ("1.4".equals(version)) {
            return SiriValidator.Version.VERSION_1_4;
        } else if ("2.0".equals(version)) {
            return SiriValidator.Version.VERSION_2_0;
        } else if ("2.1".equals(version)) {
            return SiriValidator.Version.VERSION_2_1;
        } else if ("2.0[FR-IDF-2.4]".equals(version)) {
            return SiriValidator.Version.VERSION_2_0_IDFM_2_4;
        } else if ("2.0:FR-2.4".equals(version)) {
            return SiriValidator.Version.VERSION_2_0_FR_2_4;
        }
        logger.error("Unsupported version: {}", version);
        throw new IllegalArgumentException("Unsupported version: " + version);
    }

    public static boolean hasDataOfType(Siri siri, SiriDataType type) {

        if (siri == null || siri.getServiceDelivery() == null) {
            return false;
        }

        ServiceDelivery delivery = siri.getServiceDelivery();

        switch (type) {
            case STOP_MONITORING:
                if (delivery.getStopMonitoringDeliveries() != null) {
                    for (StopMonitoringDeliveryStructure stopMonitoringDelivery : delivery.getStopMonitoringDeliveries()) {
                        if (!stopMonitoringDelivery.getMonitoredStopVisits().isEmpty()) {
                            return true;
                        }
                    }
                }
                break;
            case ESTIMATED_TIMETABLE:
                if (delivery.getEstimatedTimetableDeliveries() != null) {
                    for (EstimatedTimetableDeliveryStructure estimatedTimetableDelivery : delivery.getEstimatedTimetableDeliveries()) {
                        if (!estimatedTimetableDelivery.getEstimatedJourneyVersionFrames().isEmpty()) {
                            return true;
                        }
                    }
                }
                break;
            case SITUATION_EXCHANGE:
                if (delivery.getSituationExchangeDeliveries() != null) {
                    for (SituationExchangeDeliveryStructure situationExchangeDelivery : delivery.getSituationExchangeDeliveries()) {
                        if (!situationExchangeDelivery.getSituations().getPtSituationElements().isEmpty()) {
                            return true;
                        }
                    }
                }
                break;
            case VEHICLE_MONITORING:
                if (delivery.getVehicleMonitoringDeliveries() != null) {
                    for (VehicleMonitoringDeliveryStructure vehicleMonitoringDelivery : delivery.getVehicleMonitoringDeliveries()) {
                        if (!vehicleMonitoringDelivery.getVehicleActivities().isEmpty()) {
                            return true;
                        }
                    }
                }
                break;
            case GENERAL_MESSAGE:
                if (delivery.getGeneralMessageDeliveries() != null) {
                    for (GeneralMessageDeliveryStructure generalMessageDelivery : delivery.getGeneralMessageDeliveries()) {
                        if (!generalMessageDelivery.getGeneralMessages().isEmpty()) {
                            return true;
                        }
                    }
                }
                break;
            case FACILITY_MONITORING:
                if (delivery.getFacilityMonitoringDeliveries() != null) {
                    for (FacilityMonitoringDeliveryStructure facilityMonitoringDelivery : delivery.getFacilityMonitoringDeliveries()) {
                        if (!facilityMonitoringDelivery.getFacilityConditions().isEmpty()) {
                            return true;
                        }
                    }
                }
                break;
        }
        return false;

    }

    public static Siri setSubscriberRef(Siri siri, String subscriberRef) {
        if (StringUtils.isEmpty(subscriberRef) || siri.getServiceDelivery() == null) {
            return siri;
        }

        ServiceDelivery serviceDelivery = siri.getServiceDelivery();

        if (!serviceDelivery.getStopMonitoringDeliveries().isEmpty()) {
            for (StopMonitoringDeliveryStructure delivery : serviceDelivery.getStopMonitoringDeliveries()) {
                RequestorRef subsRef = new RequestorRef();
                subsRef.setValue(subscriberRef);
                delivery.setSubscriberRef(subsRef);
            }
        }

        if (!serviceDelivery.getEstimatedTimetableDeliveries().isEmpty()) {
            for (EstimatedTimetableDeliveryStructure delivery : serviceDelivery.getEstimatedTimetableDeliveries()) {
                RequestorRef subsRef = new RequestorRef();
                subsRef.setValue(subscriberRef);
                delivery.setSubscriberRef(subsRef);
            }
        }

        if (!serviceDelivery.getVehicleMonitoringDeliveries().isEmpty()) {
            for (VehicleMonitoringDeliveryStructure delivery : serviceDelivery.getVehicleMonitoringDeliveries()) {
                RequestorRef subsRef = new RequestorRef();
                subsRef.setValue(subscriberRef);
                delivery.setSubscriberRef(subsRef);
            }
        }

        if (!serviceDelivery.getSituationExchangeDeliveries().isEmpty()) {
            for (SituationExchangeDeliveryStructure delivery : serviceDelivery.getSituationExchangeDeliveries()) {
                RequestorRef subsRef = new RequestorRef();
                subsRef.setValue(subscriberRef);
                delivery.setSubscriberRef(subsRef);
            }
        }

        if (!serviceDelivery.getGeneralMessageDeliveries().isEmpty()) {
            for (GeneralMessageDeliveryStructure delivery : serviceDelivery.getGeneralMessageDeliveries()) {
                RequestorRef subsRef = new RequestorRef();
                subsRef.setValue(subscriberRef);
                delivery.setSubscriberRef(subsRef);
            }
        }

        if (!serviceDelivery.getFacilityMonitoringDeliveries().isEmpty()) {
            for (FacilityMonitoringDeliveryStructure delivery : serviceDelivery.getFacilityMonitoringDeliveries()) {
                RequestorRef subsRef = new RequestorRef();
                subsRef.setValue(subscriberRef);
                delivery.setSubscriberRef(subsRef);
            }
        }
        return siri;
    }

    public static List<MonitoredStopVisit> extractStopVisits(Siri siri) {
        List<MonitoredStopVisit> stopVisits = new ArrayList<>();

        if (siri.getServiceDelivery() == null || siri.getServiceDelivery().getStopMonitoringDeliveries() == null
                || siri.getServiceDelivery().getStopMonitoringDeliveries().isEmpty()) {
            return stopVisits;
        }

        for (StopMonitoringDeliveryStructure stopMonitoringDelivery : siri.getServiceDelivery().getStopMonitoringDeliveries()) {
            if (stopMonitoringDelivery.getMonitoredStopVisits() == null || stopMonitoringDelivery.getMonitoredStopVisits().isEmpty()) {
                continue;
            }
            stopVisits.addAll(stopMonitoringDelivery.getMonitoredStopVisits());
        }
        return stopVisits;
    }

    public static List<String> extractMonitoringRefs(Siri siri) {
        List<String> results = new ArrayList<>();
        List<MonitoredStopVisit> stopVisits = extractStopVisits(siri);
        if (CollectionUtils.isEmpty(stopVisits)) {
            return results;
        }

        for (MonitoredStopVisit stopVisit : stopVisits) {
            if (stopVisit.getMonitoringRef() != null) {
                results.add(stopVisit.getMonitoringRef().getValue());
            }
        }
        return results;
    }


    public static List<VehicleActivityStructure> extractVehicleActivities(Siri siri) {
        List<VehicleActivityStructure> vehicleActivities = new ArrayList<>();

        if (siri.getServiceDelivery() == null || siri.getServiceDelivery().getVehicleMonitoringDeliveries() == null
                || siri.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty()) {
            return vehicleActivities;
        }

        for (VehicleMonitoringDeliveryStructure vehicleDeliveryStruct : siri.getServiceDelivery().getVehicleMonitoringDeliveries()) {
            if (vehicleDeliveryStruct.getVehicleActivities() == null || vehicleDeliveryStruct.getVehicleActivities().isEmpty()) {
                continue;
            }
            vehicleActivities.addAll(vehicleDeliveryStruct.getVehicleActivities());
        }
        return vehicleActivities;
    }

    public static List<String> extractLineRefs(Siri siri) {
        List<String> results = new ArrayList<>();
        List<VehicleActivityStructure> activities = extractVehicleActivities(siri);
        if (CollectionUtils.isEmpty(activities)) {
            return results;
        }

        for (VehicleActivityStructure vehicleActivity : activities) {
            if (vehicleActivity.getMonitoredVehicleJourney().getLineRef() != null) {
                results.add(vehicleActivity.getMonitoredVehicleJourney().getLineRef().getValue());
            }
        }
        return results;
    }

    public static Siri filterStopMonitoringOnPreviewInterval(Siri delivery, OutboundSubscriptionSetup outboundSubscriptionSetup) {
        if (delivery.getServiceDelivery().getStopMonitoringDeliveries() == null) {
            return delivery;
        }

        List<StopMonitoringDeliveryStructure> smDeliveries = delivery.getServiceDelivery().getStopMonitoringDeliveries();
        for (StopMonitoringDeliveryStructure smDelivery : smDeliveries) {
            List<MonitoredStopVisit> filteredStopVisits = new ArrayList<>();

            for (MonitoredStopVisit monitoredStopVisit : smDelivery.getMonitoredStopVisits()) {
                if (isInWindow(monitoredStopVisit, outboundSubscriptionSetup.getPreviewInterval())) {
                    filteredStopVisits.add(monitoredStopVisit);
                }
            }
            smDelivery.getMonitoredStopVisits().clear();
            smDelivery.getMonitoredStopVisits().addAll(filteredStopVisits);
        }
        return delivery;
    }

    public static Siri removeTheoreticalSM(Siri delivery) {
        if (delivery.getServiceDelivery().getStopMonitoringDeliveries() == null) {
            return delivery;
        }

        List<StopMonitoringDeliveryStructure> smDeliveries = delivery.getServiceDelivery().getStopMonitoringDeliveries();
        for (StopMonitoringDeliveryStructure smDelivery : smDeliveries) {
            List<MonitoredStopVisit> filteredStopVisits = new ArrayList<>();

            for (MonitoredStopVisit monitoredStopVisit : smDelivery.getMonitoredStopVisits()) {
                if (monitoredStopVisit.getMonitoredVehicleJourney().isMonitored() == null || monitoredStopVisit.getMonitoredVehicleJourney().isMonitored()) {
                    filteredStopVisits.add(monitoredStopVisit);
                }


            }
            smDelivery.getMonitoredStopVisits().clear();
            smDelivery.getMonitoredStopVisits().addAll(filteredStopVisits);
        }
        return delivery;
    }


    /**
     * Determines if a notification is in previewInterval window or not
     *
     * @param stopVisit       notification to test
     * @param previewInterval window in which stopVisit must be
     * @return true : the notification is in the window and must be published
     * false : the notification is out of the window and must not be published
     */
    private static boolean isInWindow(MonitoredStopVisit stopVisit, Duration previewInterval) {
        MonitoredVehicleJourneyStructure vehicleJourney = stopVisit.getMonitoredVehicleJourney();

        if (vehicleJourney == null || vehicleJourney.getMonitoredCall() == null) {
            return false;
        }

        ZonedDateTime passingTime = null;
        if (vehicleJourney.getMonitoredCall().getAimedDepartureTime() != null) {
            passingTime = vehicleJourney.getMonitoredCall().getAimedDepartureTime();
        } else if (vehicleJourney.getMonitoredCall().getAimedArrivalTime() != null) {
            passingTime = vehicleJourney.getMonitoredCall().getAimedArrivalTime();
        } else if (vehicleJourney.getMonitoredCall().getExpectedDepartureTime() != null) {
            passingTime = vehicleJourney.getMonitoredCall().getExpectedDepartureTime();
        } else if (vehicleJourney.getMonitoredCall().getExpectedArrivalTime() != null) {
            passingTime = vehicleJourney.getMonitoredCall().getExpectedArrivalTime();
        }

        if (passingTime == null) {
            return false;
        }

        ZonedDateTime now = ZonedDateTime.now();
        GregorianCalendar calendar = GregorianCalendar.from(now);
        previewInterval.addTo(calendar);
        ZonedDateTime nowPlusDuration = calendar.toZonedDateTime();
        return passingTime.isBefore(nowPlusDuration);
    }


    public static Siri mergeSiris(Siri completeSiri, Siri dataToAdd) {
        if (completeSiri == null) {
            return dataToAdd;
        }

        mergeStopMonitoring(completeSiri, dataToAdd);
        mergeVehicleMonitoring(completeSiri, dataToAdd);
        mergeSituationExchange(completeSiri, dataToAdd);
        mergeEstimatedTimetables(completeSiri, dataToAdd);
        mergeGeneralMessages(completeSiri, dataToAdd);
        mergeFacilityMonitoring(completeSiri, dataToAdd);
        mergeCheckStatus(completeSiri, dataToAdd);
        mergeStopPoints(completeSiri, dataToAdd);
        mergeLines(completeSiri, dataToAdd);
        return completeSiri;
    }

    private static void mergeLines(Siri completeSiri, Siri dataToAdd) {
        if (dataToAdd.getLinesDelivery() == null) {
            return;
        }

        if (completeSiri.getLinesDelivery() == null) {
            completeSiri.setLinesDelivery(dataToAdd.getLinesDelivery());
            return;
        }

        completeSiri.getLinesDelivery().getAnnotatedLineReves().addAll(dataToAdd.getLinesDelivery().getAnnotatedLineReves());
    }

    private static void mergeStopPoints(Siri completeSiri, Siri dataToAdd) {
        if (dataToAdd.getStopPointsDelivery() == null) {
            return;
        }

        if (completeSiri.getStopPointsDelivery() == null) {
            completeSiri.setStopPointsDelivery(dataToAdd.getStopPointsDelivery());
            return;
        }

        completeSiri.getStopPointsDelivery().getAnnotatedStopPointReves().addAll(dataToAdd.getStopPointsDelivery().getAnnotatedStopPointReves());
    }


    private static void mergeCheckStatus(Siri completeSiri, Siri dataToAdd) {
        if (completeSiri.getCheckStatusResponse() == null) {
            completeSiri.setCheckStatusResponse(dataToAdd.getCheckStatusResponse());
        }
    }

    private static void mergeFacilityMonitoring(Siri completeSiri, Siri dataToAdd) {
        if (dataToAdd.getServiceDelivery() == null || dataToAdd.getServiceDelivery().getFacilityMonitoringDeliveries().isEmpty()) {
            return;
        }

        if (completeSiri.getServiceDelivery() == null) {
            completeSiri.setServiceDelivery(dataToAdd.getServiceDelivery());
        } else {
            completeSiri.getServiceDelivery().getFacilityMonitoringDeliveries().addAll(dataToAdd.getServiceDelivery().getFacilityMonitoringDeliveries());
        }
    }

    private static void mergeGeneralMessages(Siri completeSiri, Siri dataToAdd) {
        if (dataToAdd.getServiceDelivery() == null || dataToAdd.getServiceDelivery().getGeneralMessageDeliveries().isEmpty()) {
            return;
        }

        if (completeSiri.getServiceDelivery() == null) {
            completeSiri.setServiceDelivery(dataToAdd.getServiceDelivery());
        } else {
            completeSiri.getServiceDelivery().getGeneralMessageDeliveries().addAll(dataToAdd.getServiceDelivery().getGeneralMessageDeliveries());
        }
    }

    private static void mergeEstimatedTimetables(Siri completeSiri, Siri dataToAdd) {
        if (dataToAdd.getServiceDelivery() == null || dataToAdd.getServiceDelivery().getEstimatedTimetableDeliveries().isEmpty()) {
            return;
        }

        if (completeSiri.getServiceDelivery() == null) {
            completeSiri.setServiceDelivery(dataToAdd.getServiceDelivery());
        } else {
            completeSiri.getServiceDelivery().getEstimatedTimetableDeliveries().addAll(dataToAdd.getServiceDelivery().getEstimatedTimetableDeliveries());
        }

    }


    private static void mergeSituationExchange(Siri completeSiri, Siri dataToAdd) {
        if (dataToAdd.getServiceDelivery() == null || dataToAdd.getServiceDelivery().getSituationExchangeDeliveries().isEmpty()) {
            return;
        }

        if (completeSiri.getServiceDelivery() == null) {
            completeSiri.setServiceDelivery(dataToAdd.getServiceDelivery());
        } else {
            completeSiri.getServiceDelivery().getSituationExchangeDeliveries().addAll(dataToAdd.getServiceDelivery().getSituationExchangeDeliveries());
        }
    }

    private static void mergeVehicleMonitoring(Siri completeSiri, Siri dataToAdd) {
        if (dataToAdd.getServiceDelivery() == null || dataToAdd.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty()) {
            return;
        }

        List<VehicleActivityStructure> activities = extractVehicleActivities(completeSiri);
        activities.addAll(extractVehicleActivities(dataToAdd));

        if (completeSiri.getServiceDelivery() == null) {
            ServiceDelivery servDelivery = new ServiceDelivery();
            completeSiri.setServiceDelivery(servDelivery);
        }

        if (completeSiri.getServiceDelivery().getVehicleMonitoringDeliveries().isEmpty()) {
            VehicleMonitoringDeliveryStructure vehicleMonitoringDeliveryStructure = new VehicleMonitoringDeliveryStructure();
            vehicleMonitoringDeliveryStructure.getVehicleActivities().addAll(activities);
            completeSiri.getServiceDelivery().getVehicleMonitoringDeliveries().add(vehicleMonitoringDeliveryStructure);
        } else {
            completeSiri.getServiceDelivery().getVehicleMonitoringDeliveries().getFirst().getVehicleActivities().clear();
            completeSiri.getServiceDelivery().getVehicleMonitoringDeliveries().getFirst().getVehicleActivities().addAll(activities);
        }
    }


    private static void mergeStopMonitoring(Siri completeSiri, Siri dataToAdd) {
        if (dataToAdd.getServiceDelivery() == null || dataToAdd.getServiceDelivery().getStopMonitoringDeliveries().isEmpty()) {
            return;
        }

        if (completeSiri.getServiceDelivery() == null) {
            completeSiri.setServiceDelivery(dataToAdd.getServiceDelivery());
        } else {
            completeSiri.getServiceDelivery().getStopMonitoringDeliveries().addAll(dataToAdd.getServiceDelivery().getStopMonitoringDeliveries());
        }
    }
}
