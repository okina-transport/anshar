package no.rutebanken.anshar.subscription.fakedata;

import lombok.extern.slf4j.Slf4j;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import uk.org.siri.siri21.*;

import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
public class SiriSMDataProducer implements FakeDataProducer {

    @Override
    public List<Siri> produce(OutboundSubscriptionSetup sub) {
        List<Siri> output = new ArrayList<>();
        Set<String> stopRefFilter = getStopRefFilter(sub);
        log.info("nb of stop in filters: {}", stopRefFilter.size());
        for (String stopRef : stopRefFilter) {
            output.add(createFakeSMForStopRef(stopRef));
        }
        return output;
    }

    private Set<String> getStopRefFilter(OutboundSubscriptionSetup smSubscription) {
        Set<String> stopRefs = smSubscription.getFilterMap().get(MonitoringRefStructure.class);
        for (Map.Entry<String, Map<Class, Set<String>>> stringMapEntry : smSubscription.getFilterMapByDataset().entrySet()) {
            if (stringMapEntry.getValue().containsKey(MonitoringRefStructure.class)) {
                stopRefs.addAll(stringMapEntry.getValue().get(MonitoringRefStructure.class));
            }
        }
        return stopRefs;
    }

    private Siri createFakeSMForStopRef(String stopRef) {
        Siri siri = new Siri();
        ServiceDelivery servDel = new ServiceDelivery();
        StopMonitoringDeliveryStructure stopMonDel = new StopMonitoringDeliveryStructure();
        stopMonDel.setVersion("2.1");

        MonitoredStopVisit stopVisit1 = createFakeMonitoredStopVisit(stopRef);
        stopMonDel.getMonitoredStopVisits().add(stopVisit1);

        MonitoredStopVisit stopVisit2 = createFakeMonitoredStopVisit(stopRef);
        stopMonDel.getMonitoredStopVisits().add(stopVisit2);

        MonitoredStopVisit stopVisit3 = createFakeMonitoredStopVisit(stopRef);
        stopMonDel.getMonitoredStopVisits().add(stopVisit3);

        servDel.getStopMonitoringDeliveries().add(stopMonDel);
        siri.setServiceDelivery(servDel);

        return siri;
    }

    private MonitoredStopVisit createFakeMonitoredStopVisit(String stopRef) {
        String randomUUID = UUID.randomUUID().toString();
        MonitoredStopVisit stopVisit = new MonitoredStopVisit();
        MonitoringRefStructure monRefStruc = new MonitoringRefStructure();
        monRefStruc.setValue(stopRef);
        stopVisit.setMonitoringRef(monRefStruc);

        stopVisit.setRecordedAtTime(ZonedDateTime.now());
        stopVisit.setItemIdentifier(randomUUID);
        MonitoredVehicleJourneyStructure vehicleJourney = new MonitoredVehicleJourneyStructure();
        LineRef lineRef = new LineRef();
        lineRef.setValue(randomUUID);
        vehicleJourney.setLineRef(lineRef);
        FramedVehicleJourneyRefStructure frameVJ = new FramedVehicleJourneyRefStructure();
        frameVJ.setDatedVehicleJourneyRef(randomUUID);
        vehicleJourney.setFramedVehicleJourneyRef(frameVJ);
        vehicleJourney.setMonitored(true);

        MonitoredCallStructure monitoredCallStructure = new MonitoredCallStructure();
        StopPointRefStructure stopPointRefStructure = new StopPointRefStructure();
        stopPointRefStructure.setValue(stopRef);
        monitoredCallStructure.setStopPointRef(stopPointRefStructure);
        Random random = new Random();
        ZonedDateTime now = ZonedDateTime.now();
        int randomArrivalDelay = random.nextInt(60) + 1;
        ZonedDateTime randomArrival = now.minusMinutes(randomArrivalDelay);

        monitoredCallStructure.setAimedArrivalTime(randomArrival);
        monitoredCallStructure.setAimedDepartureTime(randomArrival);

        int randomDelay = random.nextInt(60) + 1;
        monitoredCallStructure.setExpectedArrivalTime(randomArrival.plusMinutes(randomDelay));
        monitoredCallStructure.setExpectedDepartureTime(randomArrival.plusMinutes(randomDelay));

        vehicleJourney.setMonitoredCall(monitoredCallStructure);


        stopVisit.setMonitoredVehicleJourney(vehicleJourney);

        return stopVisit;
    }

}
