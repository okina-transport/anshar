package no.rutebanken.anshar.data;

import no.rutebanken.anshar.helpers.TestObjectFactory;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri20.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FacilityMonitoringTest extends SpringBootBaseTest {

    @Autowired
    private FacilityMonitoring facilityMonitoring;

    @BeforeEach
    public void init() {
        facilityMonitoring.clearAll();
    }

    @Test
    public void testAddNull() {
        int previousSize = facilityMonitoring.getAll().size();
        facilityMonitoring.add("test", null);
        assertEquals(previousSize, facilityMonitoring.getAll().size());
    }

    @Test
    public void testAddFacilityCondition() {
        int previousSize = facilityMonitoring.getAll().size();
        FacilityConditionStructure msg = TestObjectFactory.createFacilityMonitoring();
        facilityMonitoring.add("test", msg);
        assertEquals(facilityMonitoring.getAll().size(), previousSize + 1);
    }

    @Test
    public void testUpdate() {

        FacilityConditionStructure msg = TestObjectFactory.createFacilityMonitoring();
        FacilityStatusStructure content1 = new FacilityStatusStructure();
        content1.setStatus(FacilityStatusEnumeration.ADDED);
        msg.setFacilityStatus(content1);

        //adding fmc with 1 status
        facilityMonitoring.add("test", msg);
        assertEquals(1, facilityMonitoring.getAll().size());

        Set<String> setFacility = new HashSet<>();
        setFacility.add("facility");

        Siri siri = facilityMonitoring.createServiceDelivery("test", "test", "name", null, 10,
                null, setFacility, null, null);


        FacilityStatusStructure recoveredStatus = getStatusFromFmCondition(getFacilityConditionStructureFromSiri(siri).get(0));

        assertEquals(FacilityStatusEnumeration.ADDED, recoveredStatus.getStatus());

        FacilityStatusStructure content2 = new FacilityStatusStructure();
        content2.setStatus(FacilityStatusEnumeration.AVAILABLE);
        msg.setFacilityStatus(content2);

        //adding an update of the status
        facilityMonitoring.add("test", msg);
        assertEquals(1, facilityMonitoring.getAll().size());

        siri = facilityMonitoring.createServiceDelivery("test", "test", "name", null, 10,
                null, setFacility, null, null);

        recoveredStatus = getStatusFromFmCondition(getFacilityConditionStructureFromSiri(siri).get(0));

        assertEquals(FacilityStatusEnumeration.AVAILABLE, recoveredStatus.getStatus());

    }

    @Test
    public void testChannelFilter() {

        FacilityConditionStructure msg = TestObjectFactory.createFacilityMonitoring();
        FacilityStatusStructure content1 = new FacilityStatusStructure();
        content1.setStatus(FacilityStatusEnumeration.ADDED);
        msg.setFacilityStatus(content1);

        //adding fm with 1 facility status
        facilityMonitoring.add("test", msg);
        assertEquals(1, facilityMonitoring.getAll().size());

        Set<String> setError = new HashSet<>();
        setError.add("error");
        Siri siri = facilityMonitoring.createServiceDelivery("test", null, "name", null, 10,
                null, setError, null, null);
        assertEquals(0, getFacilityConditionStructureFromSiri(siri).size(), "Delevery should be empty because a 'facility' facilityRef was added to the cache and we are asking for 'error' facilityRef");

        Set<String> setFacility = new HashSet<>();
        setFacility.add("facility");
        siri = facilityMonitoring.createServiceDelivery("test", "test", "name", null, 10,
                null, setFacility, null, null);
        assertEquals(1, getFacilityConditionStructureFromSiri(siri).size(), "Delevery should return the msg because we are asking the correct channel");
    }

    @Test
    public void testDatasetFilter() {
        FacilityConditionStructure msg = TestObjectFactory.createFacilityMonitoring();
        FacilityStatusStructure content1 = new FacilityStatusStructure();
        content1.setStatus(FacilityStatusEnumeration.ADDED);
        msg.setFacilityStatus(content1);

        //adding fm with 1 stopRef
        facilityMonitoring.add("test", msg);
        assertEquals(1, facilityMonitoring.getAll().size());

        Set<String> setFacility = new HashSet<>();
        setFacility.add("facility");

        Siri siri = facilityMonitoring.createServiceDelivery("reqRef", "wrongDataset", "name", null, 10,
                null, setFacility, null, null);
        assertEquals(0, getFacilityConditionStructureFromSiri(siri).size(), "Delevery should be empty because a fmc was added to the cache with datasetId 'test' and we are asking for dataset 'wrongDataset'");

        siri = facilityMonitoring.createServiceDelivery("reqRef", "test", "name", null, 10,
                null, setFacility, null, null);
        assertEquals(1, getFacilityConditionStructureFromSiri(siri).size(), "Delevery should return the fm because we are asking the correct datasetId");
    }


    private FacilityStatusStructure getStatusFromFmCondition(FacilityConditionStructure fmCondition) {
        if (fmCondition == null) {
            return null;
        }
        return fmCondition.getFacilityStatus();
    }


    private List<FacilityConditionStructure> getFacilityConditionStructureFromSiri(Siri siri) {

        List<FacilityConditionStructure> resultList = new ArrayList<>();

        if (siri.getServiceDelivery().getFacilityMonitoringDeliveries() == null || siri.getServiceDelivery().getFacilityMonitoringDeliveries().size() == 0) {
            return new ArrayList<>();
        }

        for (FacilityMonitoringDeliveryStructure facilityMonitoringDeliveries : siri.getServiceDelivery().getFacilityMonitoringDeliveries()) {
            resultList.addAll(facilityMonitoringDeliveries.getFacilityConditions());
        }
        return resultList;
    }


}

