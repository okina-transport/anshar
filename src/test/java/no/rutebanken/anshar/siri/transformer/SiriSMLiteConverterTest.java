package no.rutebanken.anshar.siri.transformer;

import no.rutebanken.anshar.routes.siri.transformer.SiriJsonTransformer;
import org.entur.siri21.util.SiriXml;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri21.MonitoredCallStructure;
import uk.org.siri.siri21.MonitoredStopVisit;
import uk.org.siri.siri21.MonitoredVehicleJourneyStructure;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;
import uk.org.siri.siri21.StopMonitoringDeliveryStructure;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiriSMLiteConverterTest {

    @BeforeAll
    static void setTimeZone() {
        // required by the SIRI model date adapter to unmarshal timestamps
        System.setProperty("default.time.zone", "Europe/Paris");
    }

    @Test
    void testSMJsonConversion() throws Exception {
        String smJson = Files.readString(Path.of("src/test/resources/test_sm_lite.json"), StandardCharsets.UTF_8);

        Siri siri = SiriJsonTransformer.convertJsonToSiri(smJson);

        assertNotNull(siri);
        ServiceDelivery serviceDelivery = siri.getServiceDelivery();
        assertNotNull(serviceDelivery);
        assertTrue(ZonedDateTime.parse("2026-11-13T08:27:46.359Z").isEqual(serviceDelivery.getResponseTimestamp()));
        assertEquals(1, serviceDelivery.getStopMonitoringDeliveries().size());

        StopMonitoringDeliveryStructure delivery = serviceDelivery.getStopMonitoringDeliveries().getFirst();
        assertTrue(delivery.isStatus());
        assertEquals("2.0", delivery.getVersion());
        assertNotNull(delivery.getResponseTimestamp());

        List<MonitoredStopVisit> visits = delivery.getMonitoredStopVisits();
        assertEquals(3, visits.size());

        MonitoredStopVisit visit = visits.getFirst();
        assertTrue(ZonedDateTime.parse("2026-11-13T09:26:52.000+01:00").isEqual(visit.getRecordedAtTime()));
        assertEquals("SMMAG:Item::SEM_268541857:LOC", visit.getItemIdentifier());
        assertEquals("SMMAG:StopPointRef:BP:SEM_3205:LOC", visit.getMonitoringRef().getValue());

        MonitoredVehicleJourneyStructure journey = visit.getMonitoredVehicleJourney();
        assertEquals("SMMAG:LineRef::SEM_E:LOC", journey.getLineRef().getValue());
        assertEquals("SMMAG:Version:1-23:SEM_LOC", journey.getFramedVehicleJourneyRef().getDataFrameRef().getValue());
        assertEquals("SMMAG:VehicleJourney::SEM_32471146:LOC", journey.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
        assertEquals("SMMAG:JourneyPatternRef:BP:SEM_E_A_56:LOC", journey.getJourneyPatternRef().getValue());
        assertEquals("A", journey.getDirectionNames().getFirst().getValue());
        assertEquals("SMMAG:StopRef:BP:SEM_3235:LOC", journey.getDestinationRef().getValue());
        assertEquals("Palluel", journey.getDestinationNames().getFirst().getValue());
        assertTrue(journey.isMonitored());
        assertFalse(visits.get(1).getMonitoredVehicleJourney().isMonitored());

        checkMonitoredCall(journey);

        // the converted object must be serializable to XML for the rest of the route
        String xml = SiriXml.toXml(siri);
        assertTrue(xml.contains("SMMAG:StopPointRef:BP:SEM_3205:LOC"));
    }

    private static void checkMonitoredCall(MonitoredVehicleJourneyStructure journey) {
        MonitoredCallStructure call = journey.getMonitoredCall();
        assertTrue(ZonedDateTime.parse("2026-11-13T09:29:42.000+01:00").isEqual(call.getAimedArrivalTime()));
        assertTrue(ZonedDateTime.parse("2026-11-13T09:29:42.000+01:00").isEqual(call.getAimedDepartureTime()));
        assertTrue(ZonedDateTime.parse("2026-11-13T09:30:30.000+01:00").isEqual(call.getExpectedArrivalTime()));
        assertTrue(ZonedDateTime.parse("2026-11-13T09:30:30.000+01:00").isEqual(call.getExpectedDepartureTime()));
        assertEquals("LE FONTANIL PALLUEL", call.getDestinationDisplaies().getFirst().getValue());
        assertEquals(BigInteger.valueOf(3), call.getOrder());
        assertEquals("Vallier – Libération", call.getStopPointNames().getFirst().getValue());
        assertFalse(call.isVehicleAtStop());
    }

    @Test
    void testJsonWithoutSiriRoot() {
        String json = """
                {"ServiceDelivery":{"ResponseTimestamp":"2026-11-13T10:00:00Z",
                 "StopMonitoringDelivery":[{"MonitoredStopVisit":[{"MonitoringRef":{"value":"S1"},
                   "MonitoredVehicleJourney":{"LineRef":"L1"}}]}]}}
                """;

        Siri siri = SiriJsonTransformer.convertJsonToSiri(json);

        assertNotNull(siri);
        MonitoredStopVisit visit = siri.getServiceDelivery().getStopMonitoringDeliveries().getFirst().getMonitoredStopVisits().getFirst();
        assertEquals("S1", visit.getMonitoringRef().getValue());
        assertEquals("L1", visit.getMonitoredVehicleJourney().getLineRef().getValue());
    }

    @Test
    void testInvalidJsonReturnsNull() {
        assertNull(SiriJsonTransformer.convertJsonToSiri("not a json"));
    }
}
