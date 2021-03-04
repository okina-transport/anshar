package no.rutebanken.anshar.routes;

import no.rutebanken.anshar.routes.siri.helpers.SiriObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.DestinationRef;
import uk.org.siri.siri20.JourneyPatternRef;
import uk.org.siri.siri20.LineRef;
import uk.org.siri.siri20.MonitoredCallStructure;
import uk.org.siri.siri20.MonitoredStopVisit;
import uk.org.siri.siri20.MonitoredVehicleJourneyStructure;
import uk.org.siri.siri20.MonitoringRefStructure;
import uk.org.siri.siri20.NaturalLanguageStringStructure;
import uk.org.siri.siri20.OperatorRefStructure;
import uk.org.siri.siri20.RequestorRef;
import uk.org.siri.siri20.Siri;
import uk.org.siri.siri20.StopPointRef;

import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;

import static uk.org.siri.siri20.CallStatusEnumeration.ON_TIME;

@Component
public class MockSiriFactory {

    @Autowired
    private SiriObjectFactory siriObjectFactory;

    public Siri fakeSmMessage() {
        Collection<MonitoredStopVisit> monitoredStopVisits = new ArrayList<>();

        MonitoredStopVisit monitoredStopVisit = new MonitoredStopVisit();

        monitoredStopVisit.setItemIdentifier("SIRI:75278174");

        MonitoringRefStructure monitoringRefStructure = new MonitoringRefStructure();
        monitoringRefStructure.setValue("3377704015495662");
        monitoredStopVisit.setMonitoringRef(monitoringRefStructure);

        MonitoredVehicleJourneyStructure monitoredVehicleJourney = new MonitoredVehicleJourneyStructure();

        LineRef lineRef = new LineRef();
        lineRef.setValue("35");
        monitoredVehicleJourney.setLineRef(lineRef);

        JourneyPatternRef journeyPatternRef = new JourneyPatternRef();
        journeyPatternRef.setValue("L03PR001");
        monitoredVehicleJourney.setJourneyPatternRef(journeyPatternRef);

        OperatorRefStructure operatorRefStructure = new OperatorRefStructure();
        operatorRefStructure.setValue("T2C");
        monitoredVehicleJourney.setOperatorRef(operatorRefStructure);

        DestinationRef destinationRef = new DestinationRef();
        destinationRef.setValue("2067");
        monitoredVehicleJourney.setDestinationRef(destinationRef);

        MonitoredCallStructure monitoredCallStructure = new MonitoredCallStructure();
        StopPointRef stopPointRef = new StopPointRef();
        stopPointRef.setValue("3377704015495662");
        monitoredCallStructure.setStopPointRef(stopPointRef);
        monitoredCallStructure.setOrder(BigInteger.valueOf(18));
        monitoredCallStructure.setVehicleAtStop(false);
        monitoredCallStructure.setAimedArrivalTime(ZonedDateTime.now().plusMinutes(10));
        monitoredCallStructure.setExpectedArrivalTime(ZonedDateTime.now().plusMinutes(10));
        monitoredCallStructure.setArrivalStatus(ON_TIME);
        monitoredCallStructure.setAimedDepartureTime(ZonedDateTime.now().plusMinutes(11));
        monitoredCallStructure.setExpectedDepartureTime(ZonedDateTime.now().plusMinutes(11));
        monitoredCallStructure.setDepartureStatus(ON_TIME);
        NaturalLanguageStringStructure naturalLanguageStringStructure = new NaturalLanguageStringStructure();
        naturalLanguageStringStructure.setValue("H1");
        monitoredCallStructure.setDeparturePlatformName(naturalLanguageStringStructure);

        monitoredVehicleJourney.setMonitoredCall(monitoredCallStructure);

        monitoredStopVisit.setMonitoredVehicleJourney(monitoredVehicleJourney);

        monitoredStopVisits.add(monitoredStopVisit);

        Siri smServiceDelivery = siriObjectFactory.createSMServiceDelivery(monitoredStopVisits);
        RequestorRef requestorRef = new RequestorRef();
        requestorRef.setValue("T2C");
        smServiceDelivery.getServiceDelivery().setProducerRef(requestorRef);
        return smServiceDelivery;
    }
}
