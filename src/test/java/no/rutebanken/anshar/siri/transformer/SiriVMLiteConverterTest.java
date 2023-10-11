package no.rutebanken.anshar.siri.transformer;

import no.rutebanken.anshar.routes.siri.transformer.SiriJsonTransformer;
import org.junit.jupiter.api.Test;
import uk.org.siri.siri20.*;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SiriVMLiteConverterTest {

    @Test
    public void testVMJsonConversion() throws FileNotFoundException {


        FileInputStream fileInputStream = new FileInputStream("src/test/resources/test_vm_lite.json");
        String vmJson = new BufferedReader(new InputStreamReader(fileInputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
        Siri siriObj = SiriJsonTransformer.convertJsonVMtoSiri(vmJson);
        assertNotNull(siriObj);
        assertNotNull(siriObj.getServiceDelivery());

        ServiceDelivery serviceDel = siriObj.getServiceDelivery();

        assertNotNull(serviceDel.getResponseTimestamp());
        assertNotNull(serviceDel.getRequestMessageRef());
        assertNotNull(serviceDel.getVehicleMonitoringDeliveries());
        assertFalse(serviceDel.getVehicleMonitoringDeliveries().isEmpty());

        VehicleMonitoringDeliveryStructure vehMonDel = serviceDel.getVehicleMonitoringDeliveries().get(0);
        assertNotNull(vehMonDel.getResponseTimestamp());
        assertNotNull(vehMonDel.getValidUntil());
        assertNotNull(vehMonDel.getShortestPossibleCycle());
        assertNotNull(vehMonDel.getVehicleActivities());

        assertFalse(vehMonDel.getVehicleActivities().isEmpty());
        VehicleActivityStructure vehAct = vehMonDel.getVehicleActivities().get(0);

        assertNotNull(vehAct.getRecordedAtTime());
        assertNotNull(vehAct.getMonitoredVehicleJourney());
        VehicleActivityStructure.MonitoredVehicleJourney vehJourney = vehAct.getMonitoredVehicleJourney();
        assertNotNull(vehJourney.getLineRef());
        assertNotNull(vehJourney.getDirectionRef());
        assertNotNull(vehJourney.getFramedVehicleJourneyRef());
        assertNotNull(vehJourney.getVehicleModes());
        assertNotNull(vehJourney.getVehicleModes().get(0));
        assertNotNull(vehJourney.getPublishedLineNames().get(0));
        assertNotNull(vehJourney.getDestinationNames().get(0));
        assertNotNull(vehJourney.getDestinationShortNames().get(0));
        assertNotNull(vehJourney.getDestinationRef());
        assertNotNull(vehJourney.getVehicleLocation());
        assertNotNull(vehJourney.getVehicleLocation().getLatitude());
        assertNotNull(vehJourney.getVehicleLocation().getLongitude());
        assertNotNull(vehJourney.getBearing());
        assertNotNull(vehJourney.getDelay());
        assertNotNull(vehJourney.getVehicleRef());
        assertNotNull(vehJourney.getMonitoredCall());

        MonitoredCallStructure monitoredCall = vehJourney.getMonitoredCall();
        assertNotNull(monitoredCall.getStopPointNames());
        assertNotNull(monitoredCall.getStopPointNames().get(0));
        assertNotNull(monitoredCall.getStopPointRef());
        assertNotNull(monitoredCall.getOrder());
        assertNotNull(monitoredCall.getExpectedDepartureTime());
        assertNotNull(monitoredCall.getExpectedArrivalTime());

        assertNotNull(vehJourney.getOnwardCalls());

        assertNotNull(vehJourney.getOnwardCalls().getOnwardCalls());
        for (OnwardCallStructure onwardCall : vehJourney.getOnwardCalls().getOnwardCalls()) {
            assertNotNull(onwardCall.getStopPointNames());
            assertNotNull(onwardCall.getStopPointNames().get(0));
            assertNotNull(onwardCall.getStopPointRef());
            assertNotNull(onwardCall.getOrder());
            assertNotNull(onwardCall.getExpectedArrivalTime());
        }
    }

}
