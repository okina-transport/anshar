package no.rutebanken.anshar.util;


import uk.org.siri.siri21.*;

import java.util.Optional;

public class StopMonitoringUtils {


    private static final String ENTRY_TIME_TAG_NAME = "EntryTime";


    public static Optional<String> getLineName(MonitoredStopVisit stopVisit) {
        if (stopVisit.getMonitoredVehicleJourney() == null || stopVisit.getMonitoredVehicleJourney().getLineRef() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisit.getMonitoredVehicleJourney().getLineRef().getValue());
    }

    public static Optional<String> getLineName(MonitoredStopVisitCancellation stopVisitCancellation) {
        if (stopVisitCancellation.getLineRef() == null || stopVisitCancellation.getLineRef().getValue() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisitCancellation.getLineRef().getValue());
    }

    public static Optional<String> getVehicleJourneyName(MonitoredStopVisit stopVisit) {
        if (stopVisit.getMonitoredVehicleJourney() == null || stopVisit.getMonitoredVehicleJourney().getFramedVehicleJourneyRef() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisit.getMonitoredVehicleJourney().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
    }

    public static Optional<String> getVehicleJourneyName(MonitoredStopVisitCancellation stopVisitCancellation) {
        if (stopVisitCancellation.getVehicleJourneyRef() == null || stopVisitCancellation.getVehicleJourneyRef().getDatedVehicleJourneyRef() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisitCancellation.getVehicleJourneyRef().getDatedVehicleJourneyRef());
    }

    public static Optional<String> getMonitoringRef(MonitoredStopVisit stopVisit) {
        if (stopVisit.getMonitoringRef() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(stopVisit.getMonitoringRef().getValue());
    }

    public static void initEntryTime(MonitoredStopVisit monitoredStopVisit) {
        long entryTimeStamp = System.currentTimeMillis();
        addNote(monitoredStopVisit, ENTRY_TIME_TAG_NAME, String.valueOf(entryTimeStamp));
    }

    public static void addNote(MonitoredStopVisit monitoredStopVisit, String tagName, String value) {
        NaturalLanguageStringStructure entryTime = new NaturalLanguageStringStructure();
        entryTime.setValue(tagName + ":" + value);
        monitoredStopVisit.getStopVisitNotes().add(entryTime);
    }


    public static Optional<Long> getEntryTime(MonitoredStopVisit monitoredStopVisit) {

        if (monitoredStopVisit.getStopVisitNotes() == null || monitoredStopVisit.getStopVisitNotes().isEmpty()) {
            return Optional.empty();
        }

        for (NaturalLanguageStringStructure stopVisitNote : monitoredStopVisit.getStopVisitNotes()) {
            if (stopVisitNote.getValue().contains(ENTRY_TIME_TAG_NAME + ":")) {
                return Optional.of(Long.parseLong(stopVisitNote.getValue().replace(ENTRY_TIME_TAG_NAME + ":", "")));
            }
        }
        return Optional.empty();
    }

    public static void initEntryTime(Siri siri) {
        if (siri.getServiceDelivery() != null && siri.getServiceDelivery().getStopMonitoringDeliveries() != null) {
            for (StopMonitoringDeliveryStructure stopMonitoringDelivery : siri.getServiceDelivery().getStopMonitoringDeliveries()) {
                if (stopMonitoringDelivery.getMonitoredStopVisits() != null) {
                    for (MonitoredStopVisit monitoredStopVisit : stopMonitoringDelivery.getMonitoredStopVisits()) {
                        initEntryTime(monitoredStopVisit);
                    }
                }
            }
        }
    }


}
