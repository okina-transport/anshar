package no.rutebanken.anshar.util;


import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.subscription.SiriDataType;
import org.entur.siri.validator.SiriValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.siri.siri21.*;

import javax.xml.datatype.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;

public class SiriUtils {

    private static final Logger logger = LoggerFactory.getLogger(SiriUtils.class);

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


}
