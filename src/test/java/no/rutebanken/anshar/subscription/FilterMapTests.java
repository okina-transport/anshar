package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.outbound.SiriHelper;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.*;

import java.util.Map;
import java.util.Set;

class FilterMapTests extends SpringBootBaseTest {

    @Autowired
    private SiriHelper siriHelper;


    @Test
    public void test_VM_raw_line_without_id_processings_keeped() {
        SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
        VehicleMonitoringSubscriptionStructure vmSubscription = new VehicleMonitoringSubscriptionStructure();
        VehicleMonitoringRequestStructure vmReqStructure = new VehicleMonitoringRequestStructure();
        LineRef lineRef = new LineRef();
        lineRef.setValue("3001");
        vmReqStructure.setLineRef(lineRef);
        vmSubscription.setVehicleMonitoringRequest(vmReqStructure);
        subscriptionRequest.getVehicleMonitoringSubscriptionRequests().add(vmSubscription);

        Map<Class, Set<String>> filterMap = siriHelper.getFilter(subscriptionRequest, OutboundIdMappingPolicy.ORIGINAL_ID, "DAT_WITHOUT_PROCESSING_ID");
        Assertions.assertFalse(filterMap.isEmpty());
        Assertions.assertTrue(filterMap.containsKey(LineRef.class));
        Assertions.assertTrue(filterMap.get(LineRef.class).contains("3001"));
    }

    @Test
    public void test_ET_raw_line_with_dataset_without_id_processings_keeped() {
        String dataset = "DAT_WITHOUT_PROCESSING_ID";

        SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
        EstimatedTimetableSubscriptionStructure etSubscription = new EstimatedTimetableSubscriptionStructure();

        EstimatedTimetableRequestStructure etReqStructure = new EstimatedTimetableRequestStructure();
        EstimatedTimetableRequestStructure.Lines lines = new EstimatedTimetableRequestStructure.Lines();
        LineDirectionStructure lineDirection = new LineDirectionStructure();
        LineRef lineRef = new LineRef();
        lineRef.setValue("3001");
        lineDirection.setLineRef(lineRef);
        lines.getLineDirections().add(lineDirection);
        etReqStructure.setLines(lines);
        etSubscription.setEstimatedTimetableRequest(etReqStructure);
        subscriptionRequest.getEstimatedTimetableSubscriptionRequests().add(etSubscription);


        Map<String, Map<Class, Set<String>>> filterMapByDataset = siriHelper.getFiltersByDataset(subscriptionRequest, OutboundIdMappingPolicy.ORIGINAL_ID, dataset);
        Assertions.assertFalse(filterMapByDataset.isEmpty());
        Assertions.assertTrue(filterMapByDataset.containsKey(dataset));
        Assertions.assertTrue(filterMapByDataset.get(dataset).get(LineRef.class).contains("3001"));
    }


    @Test
    public void test_ET_raw_line_without_dataset_without_id_processings_keeped() {


        SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
        EstimatedTimetableSubscriptionStructure etSubscription = new EstimatedTimetableSubscriptionStructure();

        EstimatedTimetableRequestStructure etReqStructure = new EstimatedTimetableRequestStructure();
        EstimatedTimetableRequestStructure.Lines lines = new EstimatedTimetableRequestStructure.Lines();
        LineDirectionStructure lineDirection = new LineDirectionStructure();
        LineRef lineRef = new LineRef();
        lineRef.setValue("3001");
        lineDirection.setLineRef(lineRef);
        lines.getLineDirections().add(lineDirection);
        etReqStructure.setLines(lines);
        etSubscription.setEstimatedTimetableRequest(etReqStructure);
        subscriptionRequest.getEstimatedTimetableSubscriptionRequests().add(etSubscription);


        Map<String, Map<Class, Set<String>>> filterMapByDataset = siriHelper.getFiltersByDataset(subscriptionRequest, OutboundIdMappingPolicy.ORIGINAL_ID, null);
        Assertions.assertFalse(filterMapByDataset.isEmpty());
        Assertions.assertTrue(filterMapByDataset.containsKey(ServerSubscriptionManager.DEFAULT_DATASET));
        Assertions.assertTrue(filterMapByDataset.get(ServerSubscriptionManager.DEFAULT_DATASET).get(LineRef.class).contains("3001"));
    }


}
