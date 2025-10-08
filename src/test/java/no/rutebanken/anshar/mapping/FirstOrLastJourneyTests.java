package no.rutebanken.anshar.mapping;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.helpers.TestObjectFactory;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.mapping.VehicleJourneyService;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.*;

import jakarta.xml.bind.JAXBException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FirstOrLastJourneyTests extends SpringBootBaseTest {

    @Autowired
    private VehicleJourneyService vehicleJourneyService;

    @Autowired
    private MonitoredStopVisits monitoredStopVisits;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Test
    public void test_Vj_cache() throws JAXBException {

        LocalDate date = LocalDate.of(2052, 4, 2);
        FirstOrLastJourneyEnumeration servPos1 = vehicleJourneyService.getServicePosition(date, "LA_ROCHE_SUR_YON:ServiceJourney:20944514-LRY_28-LRY_PS-Mercredi-03");
        assertEquals(FirstOrLastJourneyEnumeration.OTHER_SERVICE, servPos1);

        FirstOrLastJourneyEnumeration servPos2 = vehicleJourneyService.getServicePosition(date, "LA_ROCHE_SUR_YON:ServiceJourney:20945011-LRY_28-LRY_PS-Mercredi-03");
        assertEquals(FirstOrLastJourneyEnumeration.LAST_SERVICE_OF_DAY, servPos2);

        FirstOrLastJourneyEnumeration servPos3 = vehicleJourneyService.getServicePosition(date, "LA_ROCHE_SUR_YON:ServiceJourney:20944501-LRY_28-LRY_PS-Mercredi-03");
        assertEquals(FirstOrLastJourneyEnumeration.FIRST_SERVICE_OF_DAY, servPos3);

        FirstOrLastJourneyEnumeration servPos4 = vehicleJourneyService.getServicePosition(date, "fakeServiceJourneyId");
        assertEquals(FirstOrLastJourneyEnumeration.UNSPECIFIED, servPos4);

        LocalDate date2 = LocalDate.of(2022, 4, 2);
        FirstOrLastJourneyEnumeration servPos5 = vehicleJourneyService.getServicePosition(date2, "LA_ROCHE_SUR_YON:ServiceJourney:20944514-LRY_28-LRY_PS-Mercredi-03");
        assertEquals(FirstOrLastJourneyEnumeration.UNSPECIFIED, servPos5);
    }

    @Test
    public void test_no_VehicleJourney_ID() throws JAXBException {
        monitoredStopVisits.clearAll();
        List<MonitoredStopVisit> stopVisitToIngest = new ArrayList<>();
        MonitoredStopVisit element = TestObjectFactory.createMonitoredStopVisit(ZonedDateTime.now().plusMinutes(1), "STOP1", "aaa-bb");
        stopVisitToIngest.add(element);
        monitoredStopVisits.addAll("DAT1", stopVisitToIngest);


        Siri siriResult = getStopDataFromCache("DAT1");
        FirstOrLastJourneyEnumeration outputFirstOrLastJourney = getFirstOrLastJourneyEnumeration(siriResult);

        // No vehicle journey Id were set : firstOrLast should be : unspecified
        assertEquals(FirstOrLastJourneyEnumeration.UNSPECIFIED, outputFirstOrLastJourney);
    }

    @Test
    public void test_first_service() throws JAXBException {
        monitoredStopVisits.clearAll();

        ZonedDateTime aimedDepartureTime = ZonedDateTime.of(2052, 4, 2, 13, 15, 00, 00, ZoneId.systemDefault());
        createAndIngestStopVisit("DAT1", aimedDepartureTime, "LA_ROCHE_SUR_YON:ServiceJourney:20944501-LRY_28-LRY_PS-Mercredi-03", null);

        Siri siriResult = getStopDataFromCache("DAT1");
        FirstOrLastJourneyEnumeration outputFirstOrLastJourney = getFirstOrLastJourneyEnumeration(siriResult);

        // vehicleJourneyId is known. Result should be : first
        assertEquals(FirstOrLastJourneyEnumeration.FIRST_SERVICE_OF_DAY, outputFirstOrLastJourney);
    }


    @Test
    public void test_that_incoming_value_is_not_erased() throws JAXBException {
        monitoredStopVisits.clearAll();

        ZonedDateTime aimedDepartureTime = ZonedDateTime.of(2052, 4, 2, 13, 15, 00, 00, ZoneId.systemDefault());
        createAndIngestStopVisit("DAT1", aimedDepartureTime, "LA_ROCHE_SUR_YON:VehicleJourney:20944501-LRY_28-LRY_PS-Mercredi-03", FirstOrLastJourneyEnumeration.OTHER_SERVICE);

        Siri siriResult = getStopDataFromCache("DAT1");
        FirstOrLastJourneyEnumeration outputFirstOrLastJourney = getFirstOrLastJourneyEnumeration(siriResult);


        // valus is already existing in the incoming visit. Output value should be same as incoming value : OTHER_SERVICE
        assertEquals(FirstOrLastJourneyEnumeration.OTHER_SERVICE, outputFirstOrLastJourney);
    }


    @Test
    public void test_unknown_vehicle_journey_id() throws JAXBException {
        monitoredStopVisits.clearAll();

        ZonedDateTime aimedDepartureTime = ZonedDateTime.of(2052, 4, 2, 13, 15, 00, 00, ZoneId.systemDefault());
        createAndIngestStopVisit("DAT1", aimedDepartureTime, "ahgzdahzdzahd", null);

        Siri siriResult = getStopDataFromCache("DAT1");
        FirstOrLastJourneyEnumeration outputFirstOrLastJourney = getFirstOrLastJourneyEnumeration(siriResult);

        // VJ unknown : output value should be : UNSPECIFIED
        assertEquals(FirstOrLastJourneyEnumeration.UNSPECIFIED, outputFirstOrLastJourney);
    }


    @Test
    public void test_id_processings() throws JAXBException {
        monitoredStopVisits.clearAll();

        IdProcessingParameters dat2VJ = new IdProcessingParameters();
        dat2VJ.setObjectType(ObjectType.VEHICLE_JOURNEY);
        dat2VJ.setDatasetId("DAT2");
        dat2VJ.setOutputPrefixToAdd("LA_ROCHE_SUR_YON:ServiceJourney:");

        subscriptionConfig.getIdProcessingParameters().add(dat2VJ);

        ZonedDateTime aimedDepartureTime = ZonedDateTime.of(2052, 4, 2, 13, 15, 00, 00, ZoneId.systemDefault());
        createAndIngestStopVisit("DAT2", aimedDepartureTime, "20944501-LRY_28-LRY_PS-Mercredi-03", null);

        Siri siriResult = getStopDataFromCache("DAT2");
        FirstOrLastJourneyEnumeration outputFirstOrLastJourney = getFirstOrLastJourneyEnumeration(siriResult);

        // vehicleJourneyId is known. Result should be : first
        assertEquals(FirstOrLastJourneyEnumeration.FIRST_SERVICE_OF_DAY, outputFirstOrLastJourney);
    }

    private FirstOrLastJourneyEnumeration getFirstOrLastJourneyEnumeration(Siri siri) {
        assertTrue(siri != null);
        assertTrue(siri.getServiceDelivery() != null);
        assertTrue(siri.getServiceDelivery().getStopMonitoringDeliveries() != null);
        assertTrue(siri.getServiceDelivery().getStopMonitoringDeliveries().size() > 0);
        StopMonitoringDeliveryStructure firstDel = siri.getServiceDelivery().getStopMonitoringDeliveries().get(0);
        assertTrue(firstDel.getMonitoredStopVisits() != null && firstDel.getMonitoredStopVisits().size() > 0);
        MonitoredStopVisit visit = firstDel.getMonitoredStopVisits().get(0);
        assertTrue(visit.getMonitoredVehicleJourney() != null);
        return visit.getMonitoredVehicleJourney().getFirstOrLastJourney();
    }

    private Siri getStopDataFromCache(String datasetId) {
        Set<String> searchedStopIds = new HashSet<>();
        searchedStopIds.add("STOP1");
        return monitoredStopVisits.createServiceDelivery("req1", datasetId, new ArrayList<>(), 50000, -1, searchedStopIds);
    }

    private void createAndIngestStopVisit(String datasetId, ZonedDateTime aimedDepartureTime, String vehicleJourneyId, FirstOrLastJourneyEnumeration firstOrLastJourney) {
        MonitoredStopVisit visitToIngest = createStopVisit(aimedDepartureTime, vehicleJourneyId, firstOrLastJourney);
        List<MonitoredStopVisit> stopVisitToIngest = new ArrayList<>();
        stopVisitToIngest.add(visitToIngest);
        monitoredStopVisits.addAll(datasetId, stopVisitToIngest);
    }

    private MonitoredStopVisit createStopVisit(ZonedDateTime aimedDepartureTime, String vehicleJourneyId, FirstOrLastJourneyEnumeration firstOrLastJourney) {
        MonitoredStopVisit element = TestObjectFactory.createMonitoredStopVisit(ZonedDateTime.now().plusMinutes(1), "STOP1", "aaa-bb");
        element.getMonitoredVehicleJourney().getMonitoredCall().setAimedDepartureTime(aimedDepartureTime);
        FramedVehicleJourneyRefStructure vjFramedVehicleJourneyRef = new FramedVehicleJourneyRefStructure();
        vjFramedVehicleJourneyRef.setDatedVehicleJourneyRef(vehicleJourneyId);
        element.getMonitoredVehicleJourney().setFramedVehicleJourneyRef(vjFramedVehicleJourneyRef);

        if (firstOrLastJourney != null) {
            element.getMonitoredVehicleJourney().setFirstOrLastJourney(firstOrLastJourney);
        }
        return element;
    }


}
