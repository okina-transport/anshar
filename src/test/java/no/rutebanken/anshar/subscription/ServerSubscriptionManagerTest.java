package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.IncomingSiriParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.routes.outbound.OutboundSubscriptionSetup;
import no.rutebanken.anshar.routes.outbound.ServerSubscriptionManager;
import no.rutebanken.anshar.routes.siri.handlers.OutboundIdMappingPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.org.siri.siri21.*;

import java.util.Set;

public class ServerSubscriptionManagerTest extends SpringBootBaseTest {


    @Autowired
    private ServerSubscriptionManager outboundSubscriptionManager;

    @Autowired
    private SubscriptionConfig subscriptionConfig;


    @Test
    public void testETfilterMap() {

        IdProcessingParameters idParam1 = new IdProcessingParameters();
        idParam1.setDatasetId("ANGERS");
        idParam1.setOutputPrefixToAdd("ALM:Line:");
        idParam1.setObjectType(ObjectType.LINE);
        subscriptionConfig.getIdProcessingParameters().add(idParam1);

        IdProcessingParameters idParam2 = new IdProcessingParameters();
        idParam2.setDatasetId("LSO");
        idParam2.setOutputPrefixToAdd("LSO:Line:");
        idParam2.setOutputSuffixToAdd(":LOC");
        idParam2.setObjectType(ObjectType.LINE);
        subscriptionConfig.getIdProcessingParameters().add(idParam2);

        Siri incomingSubscriptionRequest = new Siri();
        SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
        EstimatedTimetableSubscriptionStructure etSubscription = new EstimatedTimetableSubscriptionStructure();
        EstimatedTimetableRequestStructure etReq = new EstimatedTimetableRequestStructure();
        EstimatedTimetableRequestStructure.Lines lines = new EstimatedTimetableRequestStructure.Lines();
        LineDirectionStructure line1 = new LineDirectionStructure();
        LineRef lineRef1 = new LineRef();
        lineRef1.setValue("ALM:Line:1");
        line1.setLineRef(lineRef1);
        lines.getLineDirections().add(line1);

        LineDirectionStructure line2 = new LineDirectionStructure();
        LineRef lineRef2 = new LineRef();
        lineRef2.setValue("LSO:Line:L122025:LOC");
        line2.setLineRef(lineRef2);
        lines.getLineDirections().add(line2);

        etReq.setLines(lines);

        etSubscription.setEstimatedTimetableRequest(etReq);
        subscriptionRequest.getEstimatedTimetableSubscriptionRequests().add(etSubscription);
        RequestorRef requestorRef = new RequestorRef();
        requestorRef.setValue("UNIT_TEST");
        subscriptionRequest.setRequestorRef(requestorRef);
        incomingSubscriptionRequest.setSubscriptionRequest(subscriptionRequest);
        IncomingSiriParameters incomingSiriParameters = new IncomingSiriParameters();
        incomingSiriParameters.setOutboundIdMappingPolicy(OutboundIdMappingPolicy.DEFAULT);

        OutboundSubscriptionSetup subscription = outboundSubscriptionManager.createSubscription(incomingSubscriptionRequest, incomingSiriParameters);
        Assertions.assertNotNull(subscription);
        Assertions.assertNotNull(subscription.getFilterMapByDataset());
        Assertions.assertEquals(2, subscription.getFilterMapByDataset().size());
        Set<String> angersFilters = subscription.getFilterMapByDataset().get("ANGERS").get(LineRef.class);
        Assertions.assertEquals(1, angersFilters.size());
        Assertions.assertEquals("1", angersFilters.iterator().next());

        Set<String> lsoFilters = subscription.getFilterMapByDataset().get("LSO").get(LineRef.class);
        Assertions.assertEquals(1, lsoFilters.size());
        Assertions.assertEquals("L122025", lsoFilters.iterator().next());

    }


}
