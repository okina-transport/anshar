package no.rutebanken.anshar.subscription;

import no.rutebanken.anshar.config.DiscoverySubscription;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

public class DiscoveryTests {

    @Test
    public void test_unique_consumer_address() {
        DiscoverySubscriptionCreator creator = new DiscoverySubscriptionCreator(null, null, null);
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

    @Test
    public void test_no_duplicate_subscription_when_refs_fill_last_subscription() {
        DiscoverySubscriptionCreator creator = new DiscoverySubscriptionCreator(null, null, null);
        DiscoverySubscription parentSubscription = createParentSubscription();

        List<SubscriptionSetup> createdSubs = creator.createSubscriptionsSetups(createRefs(60), parentSubscription);
        Assertions.assertEquals(2, createdSubs.size());
        Assertions.assertEquals(2, createdSubs.stream().map(SubscriptionSetup::getSubscriptionId).distinct().count());

        createdSubs = creator.createSubscriptionsSetups(createRefs(61), parentSubscription);
        Assertions.assertEquals(3, createdSubs.size());
        Assertions.assertEquals(1, createdSubs.getLast().getStopMonitoringRefValues().size());
    }

    @Test
    public void test_no_subscription_created_without_refs() {
        DiscoverySubscriptionCreator creator = new DiscoverySubscriptionCreator(null, null, null);

        List<SubscriptionSetup> createdSubs = creator.createSubscriptionsSetups(List.of(), createParentSubscription());
        Assertions.assertTrue(createdSubs.isEmpty());
    }

    private static List<String> createRefs(int nbOfRefs) {
        return IntStream.range(0, nbOfRefs).mapToObj(i -> "test" + i).toList();
    }

    private static DiscoverySubscription createParentSubscription() {
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
        return parentSubscription;
    }
}
