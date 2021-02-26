/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package no.rutebanken.anshar.data;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.MonitoredCallStructure;
import uk.org.siri.siri20.MonitoredStopVisit;
import uk.org.siri.siri20.MonitoredVehicleJourneyStructure;
import uk.org.siri.siri20.MonitoringRefStructure;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class MonitoredStopVisitsTest extends SpringBootBaseTest {


    @Autowired
    private MonitoredStopVisits monitoredStopVisits;
    
    @BeforeEach
    public void init() {
        monitoredStopVisits.clearAll();
    }

    @Test
    public void testAddMonitoredStopVisit() {
        int previousSize = monitoredStopVisits.getAll().size();
        MonitoredStopVisit element = createMonitoredStopVisitStructure(
                                                    ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        monitoredStopVisits.add("test", element);
        assertEquals("Vehicle not added", previousSize + 1, monitoredStopVisits.getAll().size());
    }

    @Test
    public void testNullStopReference() {
        int previousSize = monitoredStopVisits.getAll().size();

        monitoredStopVisits.add("test", null);
        assertEquals("Null-element added", previousSize, monitoredStopVisits.getAll().size());
    }

    @Test
    public void testUpdatedVehicle() {
        int previousSize = vehicleActivities.getAll().size();

        //Add element
        String vehicleReference = UUID.randomUUID().toString();
        VehicleActivityStructure element = createMonitoredStopVisitStructure(
                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);

        vehicleActivities.add("test", element);
        //Verify that element is added
        assertEquals(previousSize + 1, vehicleActivities.getAll().size());

        //Update element
        VehicleActivityStructure element2 = createMonitoredStopVisitStructure(
                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);

        VehicleActivityStructure updatedVehicle = vehicleActivities.add("test", element2);

        //Verify that activity is found as updated
        assertNotNull(updatedVehicle);
        //Verify that existing element is updated
        assertTrue(vehicleActivities.getAll().size() == previousSize + 1);

        //Add brand new element
        VehicleActivityStructure element3 = createMonitoredStopVisitStructure(
                ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        updatedVehicle = vehicleActivities.add("test", element3);

        //Verify that activity is found as new
        assertNotNull(updatedVehicle);
        //Verify that new element is added
        assertEquals(previousSize + 2, vehicleActivities.getAll().size());

        vehicleActivities.add("test2", element3);
        //Verify that new element is added
        assertEquals(previousSize + 3, vehicleActivities.getAll().size());

        //Verify that element added is vendor-specific
        assertEquals(previousSize + 2, vehicleActivities.getAll("test").size());
    }
//
//    @Test
//    @Disabled
//    public void testUpdatedVehicleWrongOrder() {
//
//        //Add element
//        String vehicleReference = UUID.randomUUID().toString();
//        VehicleActivityStructure element = createMonitoredStopVisitStructure(
//                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);
//        ProgressBetweenStopsStructure progress = new ProgressBetweenStopsStructure();
//        progress.setPercentage(BigDecimal.ONE);
//        element.setProgressBetweenStops(progress);
//        element.setRecordedAtTime(ZonedDateTime.now().plusMinutes(1));
//
//        vehicleActivities.add("test", element);
//
//        VehicleActivityStructure testOriginal = vehicleActivities.add("test", element);
//
//        assertEquals("VM has not been added.", BigDecimal.ONE, testOriginal.getProgressBetweenStops().getPercentage());
//
//        //Update element
//        VehicleActivityStructure element2 = createMonitoredStopVisitStructure(
//                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);
//
//        ProgressBetweenStopsStructure progress2 = new ProgressBetweenStopsStructure();
//        progress2.setPercentage(BigDecimal.TEN);
//        element2.setProgressBetweenStops(progress2);
//
//        //Update is recorder BEFORE current - should be ignored
//        element2.setRecordedAtTime(ZonedDateTime.now());
//
//        VehicleActivityStructure test = vehicleActivities.add("test", element2);
//
//        assertEquals("VM has been wrongfully updated", BigDecimal.ONE, test.getProgressBetweenStops().getPercentage());
//    }
//
//    @Test
//    @Disabled
//    public void testUpdatedVehicleNoRecordedAtTime() {
//
//        //Add element
//        String vehicleReference = UUID.randomUUID().toString();
//        VehicleActivityStructure element = createMonitoredStopVisitStructure(
//                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);
//        ProgressBetweenStopsStructure progress = new ProgressBetweenStopsStructure();
//        progress.setPercentage(BigDecimal.ONE);
//        element.setProgressBetweenStops(progress);
//        element.setRecordedAtTime(null);
//
//        vehicleActivities.add("test", element);
//
//        VehicleActivityStructure testOriginal = vehicleActivities.add("test", element);
//
//        assertEquals("VM has not been added.", BigDecimal.ONE, testOriginal.getProgressBetweenStops().getPercentage());
//
//        //Update element
//        VehicleActivityStructure element2 = createMonitoredStopVisitStructure(
//                                                    ZonedDateTime.now().plusMinutes(1), vehicleReference);
//
//        ProgressBetweenStopsStructure progress2 = new ProgressBetweenStopsStructure();
//        progress2.setPercentage(BigDecimal.TEN);
//        element2.setProgressBetweenStops(progress2);
//
//        element2.setRecordedAtTime(null);
//
//        VehicleActivityStructure test = vehicleActivities.add("test", element2);
//
//        assertEquals("VM has been wrongfully updated", BigDecimal.ONE, test.getProgressBetweenStops().getPercentage());
//    }
//
//    @Test
//    public void testGetUpdatesOnly() {
//        int previousSize = vehicleActivities.getAll().size();
//
//        String prefix = "updateOnly-";
//        vehicleActivities.add("test", createMonitoredStopVisitStructure(ZonedDateTime.now(), prefix+"1234"));
//        vehicleActivities.add("test", createMonitoredStopVisitStructure(ZonedDateTime.now(), prefix+"2345"));
//        vehicleActivities.add("test", createMonitoredStopVisitStructure(ZonedDateTime.now(), prefix+"3456"));
//
//        sleep(250);
//
//        // Added 3
//        assertEquals(previousSize+3, vehicleActivities.getAllUpdates("1234-1234", null).size());
//
//        vehicleActivities.add("test", createMonitoredStopVisitStructure(ZonedDateTime.now(), prefix+"4567"));
//        sleep(250);
//
//        //Added one
//        assertEquals(1, vehicleActivities.getAllUpdates("1234-1234", null).size());
//
//
//        //None added
//        assertEquals(0, vehicleActivities.getAllUpdates("1234-1234", null).size());
//
//        //Verify that all elements still exist
//        assertEquals(previousSize+4, vehicleActivities.getAll().size());
//    }
//
    private MonitoredStopVisit createMonitoredStopVisitStructure(ZonedDateTime recordedAtTime, String stopReference) {
        MonitoredStopVisit element = new MonitoredStopVisit();

        element.setRecordedAtTime(recordedAtTime);
        MonitoringRefStructure monitoringRefStructure = new MonitoringRefStructure();
        monitoringRefStructure.setValue(stopReference);
        element.setMonitoringRef(monitoringRefStructure);

        MonitoredVehicleJourneyStructure monitoredVehicleJourneyStructure = new MonitoredVehicleJourneyStructure();
        MonitoredCallStructure monitoredCallStructure = new MonitoredCallStructure();
        monitoredCallStructure.setExpectedArrivalTime(ZonedDateTime.now().plusHours(1));
        monitoredVehicleJourneyStructure.setMonitoredCall(monitoredCallStructure);
        element.setMonitoredVehicleJourney(monitoredVehicleJourneyStructure);
        return element;
    }
//
//
//    @Test
//    public void testExcludeDatasetIds() {
//
//        String prefix = "excludedOnly-";
//
//        VehicleActivityStructure activity_1 = createMonitoredStopVisitStructure(ZonedDateTime.now(), prefix + "1234");
//        activity_1.getMonitoredVehicleJourney().setDataSource("test1");
//        vehicleActivities.add("test1", activity_1);
//
//        VehicleActivityStructure activity_2 = createMonitoredStopVisitStructure(ZonedDateTime.now(), prefix + "2345");
//        activity_2.getMonitoredVehicleJourney().setDataSource("test2");
//        vehicleActivities.add("test2", activity_2);
//
//        VehicleActivityStructure activity_3 = createMonitoredStopVisitStructure(ZonedDateTime.now(), prefix + "3456");
//        activity_3.getMonitoredVehicleJourney().setDataSource("test3");
//        vehicleActivities.add("test3", activity_3);
//
//        assertExcludedId("test1");
//        assertExcludedId("test2");
//        assertExcludedId("test3");
//    }
//
//    private void assertExcludedId(String excludedDatasetId) {
//        Siri serviceDelivery = vehicleActivities.createServiceDelivery(null, null, null, Arrays.asList(excludedDatasetId), 100);
//
//        List<VehicleActivityStructure> vehicleActivities = serviceDelivery.getServiceDelivery().getVehicleMonitoringDeliveries().get(0).getVehicleActivities();
//
//        assertEquals(2, vehicleActivities.size());
//        for (VehicleActivityStructure activity : vehicleActivities) {
//            assertFalse(activity.getMonitoredVehicleJourney().getDataSource().equals(excludedDatasetId));
//        }
//    }

}
