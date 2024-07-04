package no.rutebanken.anshar.idTests.vehicleMonitoring;

import no.rutebanken.anshar.data.VehicleActivities;
import no.rutebanken.anshar.helpers.TestObjectFactory;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri20.VehicleActivityStructure;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VM_SpecialCharactersReplacement extends SpringBootBaseTest {

    @Autowired
    private VehicleActivities vehicleActivities;

    @BeforeEach
    public void init() {
        vehicleActivities.clearAll();
    }

    @Test
    public void testReplaceOnDatedVehicleRef() {
        int previousSize = vehicleActivities.getAll().size();
        VehicleActivityStructure element = TestObjectFactory.createVehicleActivityStructure(
                ZonedDateTime.now().plusMinutes(1), UUID.randomUUID().toString(), "");


        FramedVehicleJourneyRefStructure framedVehicleJourneyRef = new FramedVehicleJourneyRefStructure();
        framedVehicleJourneyRef.setDatedVehicleJourneyRef("PREF:ServiceJourney:id_with::and||end:LOC");
        element.getMonitoredVehicleJourney().setFramedVehicleJourneyRef(framedVehicleJourneyRef);
        vehicleActivities.add("test", element);
        assertEquals(previousSize + 1, vehicleActivities.getAll().size(), "Vehicle not added");

        VehicleActivityStructure vehicleAct = vehicleActivities.getAll().stream().findFirst().get();
        Assert.assertEquals("PREF:ServiceJourney:id_with--and__end:LOC", vehicleAct.getMonitoredVehicleJourney().getFramedVehicleJourneyRef().getDatedVehicleJourneyRef());
    }

}
