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
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.CallStatusEnumeration;
import uk.org.siri.siri20.MonitoredCallStructure;
import uk.org.siri.siri20.MonitoredStopVisit;
import uk.org.siri.siri20.MonitoredVehicleJourneyStructure;
import uk.org.siri.siri20.MonitoringRefStructure;
import uk.org.siri.siri20.Siri;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static no.rutebanken.anshar.helpers.SleepUtil.sleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        MonitoredStopVisit element = createMonitoredStopVisit(
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
    public void testUpdatedMonitoredStopvisit() {
        int previousSize = monitoredStopVisits.getAll().size();

        //Add element
        String stopReference = UUID.randomUUID().toString();
        String itempIdentifier = UUID.randomUUID().toString();
        MonitoredStopVisit element = createMonitoredStopVisit(ZonedDateTime.now().plusMinutes(1), stopReference, itempIdentifier);

        monitoredStopVisits.add("test", element);
        //Verify that element is added
        assertEquals(previousSize + 1, monitoredStopVisits.getAll().size());

        //Update element
        MonitoredStopVisit element2 = createMonitoredStopVisit(ZonedDateTime.now().plusMinutes(1), stopReference, itempIdentifier);

        MonitoredStopVisit updatedMonitoredStopVisit = monitoredStopVisits.add("test", element2);

        //Verify that activity is found as updated
        assertNotNull(updatedMonitoredStopVisit);
        //Verify that existing element is updated
        assertEquals(previousSize + 1, monitoredStopVisits.getAll().size());

        //Add brand new element
        MonitoredStopVisit element3 = createMonitoredStopVisit(
                ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString(), UUID.randomUUID().toString());

        updatedMonitoredStopVisit = monitoredStopVisits.add("test", element3);

        //Verify that activity is found as new
        assertNotNull(updatedMonitoredStopVisit);
        //Verify that new element is added
        assertEquals(previousSize + 2, monitoredStopVisits.getAll().size());

        monitoredStopVisits.add("test2", element3);
        //Verify that new element is added
        assertEquals(previousSize + 3, monitoredStopVisits.getAll().size());

        //Verify that element added is vendor-specific
        assertEquals(previousSize + 2, monitoredStopVisits.getAll("test").size());
    }


    @Test
    public void testUpdatedMonitoredStopvisitWithMonitoredChanges() {
        int previousSize = monitoredStopVisits.getAll().size();

        //Add element
        String stopReference = UUID.randomUUID().toString();
        String itempIdentifier = UUID.randomUUID().toString();
        ZonedDateTime arrivalTime = ZonedDateTime.now().plusMinutes(2);


        //First call is created with monitored status  : false
        MonitoredStopVisit element = createMonitoredStopVisit(arrivalTime, stopReference, itempIdentifier);
        element.getMonitoredVehicleJourney().setMonitored(false);
        MonitoredCallStructure monitoredCall = element.getMonitoredVehicleJourney().getMonitoredCall();
        monitoredCall.setAimedArrivalTime(arrivalTime);
        monitoredCall.setExpectedArrivalTime(arrivalTime);
        monitoredCall.setAimedDepartureTime(arrivalTime);
        monitoredCall.setExpectedDepartureTime(arrivalTime);
        monitoredCall.setDepartureStatus(CallStatusEnumeration.ON_TIME);

        monitoredStopVisits.add("test", element);
        //Verify that element is added
        assertEquals(previousSize + 1, monitoredStopVisits.getAll().size());

        //Update element
        //second call with exactly same hours but monitored status : true
        //First call is created with monitored status  : false
        MonitoredStopVisit element2 = createMonitoredStopVisit(arrivalTime, stopReference, itempIdentifier);

        MonitoredCallStructure monitoredCall2 = element2.getMonitoredVehicleJourney().getMonitoredCall();
        element2.getMonitoredVehicleJourney().setMonitored(true);
        monitoredCall2.setAimedArrivalTime(arrivalTime);
        monitoredCall2.setExpectedArrivalTime(arrivalTime);
        monitoredCall2.setAimedDepartureTime(arrivalTime);
        monitoredCall2.setExpectedDepartureTime(arrivalTime);
        monitoredCall2.setDepartureStatus(CallStatusEnumeration.ON_TIME);

        MonitoredStopVisit updatedMonitoredStopVisit = monitoredStopVisits.add("test", element2);

        //Verify that activity is found as updated
        assertNotNull(updatedMonitoredStopVisit);
        //Verify that existing element is updated
        assertEquals(previousSize + 1, monitoredStopVisits.getAll().size());

        Collection<MonitoredStopVisit> allVisits = monitoredStopVisits.getAll();
        MonitoredStopVisit first = allVisits.iterator().next();

        //After second call, monitored status should be changed to true
        assertTrue(first.getMonitoredVehicleJourney().isMonitored());


    }



    @Test
    public void testGetUpdatesOnly() {
        int previousSize = monitoredStopVisits.getAll().size();

        String prefix = "updateOnly-";
        monitoredStopVisits.add("test", createMonitoredStopVisit(ZonedDateTime.now(), prefix+"1234"));
        monitoredStopVisits.add("test", createMonitoredStopVisit(ZonedDateTime.now(), prefix+"2345"));
        monitoredStopVisits.add("test", createMonitoredStopVisit(ZonedDateTime.now(), prefix+"3456"));

        sleep(250);

        // Added 3
        assertEquals(previousSize+3, monitoredStopVisits.getAllUpdates("1234-1234", null).size());

        monitoredStopVisits.add("test", createMonitoredStopVisit(ZonedDateTime.now(), prefix+"4567"));
        sleep(250);

        //Added one
        assertEquals(1, monitoredStopVisits.getAllUpdates("1234-1234", null).size());



        //Verify that all elements still exist
        assertEquals(previousSize+4, monitoredStopVisits.getAll().size());
    }


    private MonitoredStopVisit createMonitoredStopVisit(ZonedDateTime recordedAtTime, String stopReference) {
        return createMonitoredStopVisit(recordedAtTime, stopReference, UUID.randomUUID().toString());
    }

    private MonitoredStopVisit createMonitoredStopVisit(ZonedDateTime recordedAtTime, String stopReference, String itemIdentifier) {
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

        element.setItemIdentifier(itemIdentifier);
        return element;
    }


    @Test
    @Ignore // TODO MHI à faire
    public void testExcludeDatasetIds() {

        String prefix = "excludedOnly-";

        MonitoredStopVisit activity_1 = createMonitoredStopVisit(ZonedDateTime.now(), prefix + "1234");
        activity_1.getMonitoredVehicleJourney().setDataSource("test1");
        monitoredStopVisits.add("test1", activity_1);

        MonitoredStopVisit activity_2 = createMonitoredStopVisit(ZonedDateTime.now(), prefix + "2345");
        activity_2.getMonitoredVehicleJourney().setDataSource("test2");
        monitoredStopVisits.add("test2", activity_2);

        MonitoredStopVisit activity_3 = createMonitoredStopVisit(ZonedDateTime.now(), prefix + "3456");
        activity_3.getMonitoredVehicleJourney().setDataSource("test3");
        monitoredStopVisits.add("test3", activity_3);

        assertExcludedId("test1");
        assertExcludedId("test2");
        assertExcludedId("test3");
    }

    private void assertExcludedId(String excludedDatasetId) {
        Siri serviceDelivery = monitoredStopVisits.createServiceDelivery(null, null, null, Arrays.asList(excludedDatasetId), 100, -1);

        List<MonitoredStopVisit> monitoredStopVisits = serviceDelivery.getServiceDelivery().getStopMonitoringDeliveries().get(0).getMonitoredStopVisits();

        assertEquals(2, monitoredStopVisits.size());
        for (MonitoredStopVisit activity : monitoredStopVisits) {
            assertFalse(activity.getMonitoredVehicleJourney().getDataSource().equals(excludedDatasetId));
        }
    }

}
