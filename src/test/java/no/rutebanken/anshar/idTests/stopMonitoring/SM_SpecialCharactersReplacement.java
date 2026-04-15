package no.rutebanken.anshar.idTests.stopMonitoring;

import no.rutebanken.anshar.data.MonitoredStopVisits;
import no.rutebanken.anshar.idTests.TestUtils;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri21.LineRef;
import uk.org.siri.siri21.MonitoredStopVisit;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class SM_SpecialCharactersReplacement extends SpringBootBaseTest {

    @Autowired
    private MonitoredStopVisits monitoredStopVisits;

    @BeforeEach
    public void init() {
        monitoredStopVisits.clearAll();
    }


    @Test
    public void testAddMonitoredStopVisit() {

        MonitoredStopVisit element = TestUtils.createMonitoredStopVisit(
                ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString());

        FramedVehicleJourneyRefStructure framedVehicleJourneyRef = new FramedVehicleJourneyRefStructure();
        framedVehicleJourneyRef.setDatedVehicleJourneyRef("vj:id_with::and||end");
        element.getMonitoredVehicleJourney().setFramedVehicleJourneyRef(framedVehicleJourneyRef);
        LineRef lineRef = new LineRef();
        lineRef.setValue("line##3A##id");
        element.getMonitoredVehicleJourney().setLineRef(lineRef);
        monitoredStopVisits.add("test", element);
        assertEquals(1, monitoredStopVisits.getAll().size());
        MonitoredStopVisit monitoredStopvisit = monitoredStopVisits.getAll().stream().findFirst().get();
        assertEquals("vj-id_with--and__end", monitoredStopvisit.getMonitoredVehicleJourney().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
        assertEquals("line-id", monitoredStopvisit.getMonitoredVehicleJourney().getLineRef().getValue());
    }
}
