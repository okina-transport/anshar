package no.rutebanken.anshar.mapping;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.StopTimesService;
import no.rutebanken.anshar.routes.siri.processor.UpdateVJIdProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.siri.siri21.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RecomputeVehicleJourneyIdFromTheoretical extends SpringBootBaseTest {

    @Mock
    private StopTimesService stopTimesService;

    private static final String DATASET_ID = "DAT1";
    private static final String LINE_ID = "LINE1";
    private static final String STOP_ID = "STOP1";
    private static final String DIRECTION_ID = "A";
    private static final String DESTINATION_ID = "STOP2";
    private static final String EXPECTED_VJ_ID_FROM_TH = "TH_VJ_ID_1";


    @Test
    public void test_vjid_replacement_by_theoretical() {

        ZonedDateTime now = ZonedDateTime.of(2052, 01, 01, 12, 00, 00, 00, ZoneId.systemDefault());
        when(stopTimesService.findTripIdByStopAndTime(DATASET_ID, LINE_ID, STOP_ID, DIRECTION_ID, DESTINATION_ID, now)).thenReturn(Optional.of(EXPECTED_VJ_ID_FROM_TH));
        UpdateVJIdProcessor updateVjProc = new UpdateVJIdProcessor(DATASET_ID, stopTimesService);


        // siri has been generated with wrong VJ id
        Siri siri = generateSiriWithVJ(now);

        // launching vj id replacement
        updateVjProc.process(siri);

        // expected vj to be replaced by TH data
        assertEquals(EXPECTED_VJ_ID_FROM_TH, siri.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst().getMonitoredVehicleJourney().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
    }

    @Test
    public void test_vjid_not_replaced_because_unknown() {

        ZonedDateTime now = ZonedDateTime.of(2052, 01, 01, 12, 00, 00, 00, ZoneId.systemDefault());
        when(stopTimesService.findTripIdByStopAndTime(DATASET_ID, LINE_ID, STOP_ID, DIRECTION_ID, DESTINATION_ID, now.plusMinutes(10))).thenReturn(Optional.empty());
        UpdateVJIdProcessor updateVjProc = new UpdateVJIdProcessor(DATASET_ID, stopTimesService);

        // siri has been generated with wrong VJ id
        Siri siri = generateSiriWithVJ(now.plusMinutes(10));

        // launching vj id replacement
        updateVjProc.process(siri);

        // expected vj to not be changed
        assertEquals("WRONG VJ FROM TR", siri.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst().getMonitoredVehicleJourney().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
    }


    private Siri generateSiriWithVJ(ZonedDateTime arrivalTime) {
        Siri siri = new Siri();
        ServiceDelivery sd = new ServiceDelivery();
        StopMonitoringDeliveryStructure smds = new StopMonitoringDeliveryStructure();
        MonitoredStopVisit monitoredStopVisit = new MonitoredStopVisit();
        MonitoringRefStructure monitoringRefStructure = new MonitoringRefStructure();
        monitoringRefStructure.setValue(STOP_ID);
        monitoredStopVisit.setMonitoringRef(monitoringRefStructure);
        MonitoredVehicleJourneyStructure monitoredVehicleJourney = new MonitoredVehicleJourneyStructure();
        LineRef lineRef = new LineRef();
        lineRef.setValue(LINE_ID);
        monitoredVehicleJourney.setLineRef(lineRef);
        NaturalLanguageStringStructure directionName = new NaturalLanguageStringStructure();
        directionName.setValue(DIRECTION_ID);
        monitoredVehicleJourney.getDirectionNames().add(directionName);
        DestinationRef destinationRef = new DestinationRef();
        destinationRef.setValue(DESTINATION_ID);
        monitoredVehicleJourney.setDestinationRef(destinationRef);
        FramedVehicleJourneyRefStructure framedVehicleJourneyRef = new FramedVehicleJourneyRefStructure();
        framedVehicleJourneyRef.setDatedVehicleJourneyRef("WRONG VJ FROM TR");
        monitoredVehicleJourney.setFramedVehicleJourneyRef(framedVehicleJourneyRef);
        MonitoredCallStructure monitoredCall = new MonitoredCallStructure();
        monitoredCall.setAimedArrivalTime(arrivalTime);
        monitoredVehicleJourney.setMonitoredCall(monitoredCall);
        monitoredVehicleJourney.setMonitored(true);
        monitoredStopVisit.setMonitoredVehicleJourney(monitoredVehicleJourney);
        smds.getMonitoredStopVisits().add(monitoredStopVisit);
        sd.getStopMonitoringDeliveries().add(smds);
        siri.setServiceDelivery(sd);

        return siri;
    }


}
