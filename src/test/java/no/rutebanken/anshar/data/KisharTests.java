package no.rutebanken.anshar.data;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.siri.handlers.inbound.StopMonitoringInbound;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static no.rutebanken.anshar.idTests.TestUtils.createMonitoredStopVisit;


/**
 * Utility test class to ingest data that will be sent to kishar
 * To be run manually. Should not be run by mvn
 */
public class KisharTests extends SpringBootBaseTest {

    @Autowired
    private StopMonitoringInbound stopMonitoringInbound;

    @Test
    @Disabled
    public void sendToKishar() {

        ZonedDateTime sp1Arrival = ZonedDateTime.now().plusHours(1);
        MonitoredStopVisit vjA_sp1 = createCustomMonitoredStopVisit("35813219-CR_24_25-HS25H2C1-L-Ma-Me-J-00", "STOP1", sp1Arrival);
        List<MonitoredStopVisit> visitsToIngest = new ArrayList<>();
        visitsToIngest.add(vjA_sp1);

        stopMonitoringInbound.ingestStopVisits("NAOLIBORG", visitsToIngest);

        MonitoredStopVisit vjA_sp2 = createCustomMonitoredStopVisit("35813219-CR_24_25-HS25H2C1-L-Ma-Me-J-00", "STOP2", sp1Arrival.plusMinutes(10));
        visitsToIngest.clear();
        visitsToIngest.add(vjA_sp2);

        stopMonitoringInbound.ingestStopVisits("NAOLIBORG", visitsToIngest);

    }

    @Test
    @Disabled
    public void sendToKisharSP1only() {

        ZonedDateTime sp1Arrival = ZonedDateTime.now().plusHours(1);
        MonitoredStopVisit vjA_sp1 = createCustomMonitoredStopVisit("35813219-CR_24_25-HS25H2C1-L-Ma-Me-J-00", "STOP1", sp1Arrival);
        List<MonitoredStopVisit> visitsToIngest = new ArrayList<>();
        visitsToIngest.add(vjA_sp1);

        stopMonitoringInbound.ingestStopVisits("NAOLIBORG", visitsToIngest);


    }

    @Test
    @Disabled
    public void sendToKisharVJBonly() {

        ZonedDateTime sp1Arrival = ZonedDateTime.now().plusHours(2);
        MonitoredStopVisit vjA_sp1 = createCustomMonitoredStopVisit("B", "STOP1", sp1Arrival);
        List<MonitoredStopVisit> visitsToIngest = new ArrayList<>();
        visitsToIngest.add(vjA_sp1);

        stopMonitoringInbound.ingestStopVisits("NAOLIBORG", visitsToIngest);


    }

    private MonitoredStopVisit createCustomMonitoredStopVisit(String vehicleJourneyId, String stopId, ZonedDateTime expectedTime) {
        MonitoredStopVisit element = createMonitoredStopVisit(
                ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        StopPointRefStructure stopPoint = new StopPointRefStructure();
        stopPoint.setValue(stopId);
        MonitoredVehicleJourneyStructure vehicleJourney = element.getMonitoredVehicleJourney();
        vehicleJourney.getMonitoredCall().setStopPointRef(stopPoint);

        MonitoringRefStructure monitoringRef = new MonitoringRefStructure();
        monitoringRef.setValue(stopId);
        element.setMonitoringRef(monitoringRef);

        LineRef lineRef = new LineRef();
        lineRef.setValue("3");
        vehicleJourney.setLineRef(lineRef);

        FramedVehicleJourneyRefStructure frameStruc = new FramedVehicleJourneyRefStructure();
        DataFrameRefStructure dataFrameRef = new DataFrameRefStructure();
        dataFrameRef.setValue("any");
        frameStruc.setDataFrameRef(dataFrameRef);
        frameStruc.setDatedVehicleJourneyRef(vehicleJourneyId);
        vehicleJourney.setFramedVehicleJourneyRef(frameStruc);

        vehicleJourney.getVehicleModes().add(VehicleModesEnumeration.TRAM);

        NaturalLanguageStringStructure dirName = new NaturalLanguageStringStructure();
        dirName.setValue("blbla arret");
        vehicleJourney.getDirectionNames().add(dirName);
        DirectionRefStructure directionRef = new DirectionRefStructure();
        directionRef.setValue("FR_NAOLIB:StopPlace:311");
        vehicleJourney.setDirectionRef(directionRef);

        MonitoredCallStructure monitoredCall = element.getMonitoredVehicleJourney().getMonitoredCall();
        monitoredCall.setExpectedArrivalTime(expectedTime);

        monitoredCall.setAimedArrivalTime(monitoredCall.getExpectedArrivalTime().minusMinutes(1));

        monitoredCall.setAimedDepartureTime(monitoredCall.getAimedArrivalTime());
        monitoredCall.setExpectedDepartureTime(monitoredCall.getExpectedArrivalTime());
        return element;

    }
}
