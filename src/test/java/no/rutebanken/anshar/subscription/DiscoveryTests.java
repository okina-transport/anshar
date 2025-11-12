package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.config.DiscoverySubscription;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class DiscoveryTests {

    @Test
    public void test_unique_consumer_address() {
        DiscoverySubscriptionCreator creator = new DiscoverySubscriptionCreator(null, null);
        List<String> monitoringRefs = List.of("test1", "test2", "test3");
        DiscoverySubscription parentSubscription = new DiscoverySubscription();
        parentSubscription.setDiscoveryType(SiriDataType.STOP_MONITORING);
        parentSubscription.setVersion("2.0");
        parentSubscription.setDatasetId("DAT1");
        parentSubscription.setSubscriptionIdBase("PARENT-ID");
        parentSubscription.setVendorBaseName("PARENT_VENDOR");
        parentSubscription.setServiceType(SubscriptionSetup.ServiceType.SOAP);
        parentSubscription.setRequestorRef("PARENT-REQ");
        parentSubscription.setChangeBeforeUpdatesSeconds(30);
        parentSubscription.setPreviewIntervalSeconds(10800);
        parentSubscription.setUrl("http://parentURL.com");


        List<SubscriptionSetup> createdSubs = creator.createSubscriptionsSetups(monitoringRefs, parentSubscription);
        Assertions.assertFalse(createdSubs.isEmpty());
        Assertions.assertEquals("null/2.0/ws/PARENT_VENDOR/PARENT-ID", createdSubs.getFirst().buildUrl());
    }
}
