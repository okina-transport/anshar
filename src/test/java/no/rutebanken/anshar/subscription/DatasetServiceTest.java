package no.rutebanken.anshar.subscription;

import com.hazelcast.replicatedmap.ReplicatedMap;
import no.rutebanken.anshar.api.SiriApi;
import no.rutebanken.anshar.config.DiscoverySubscription;
import no.rutebanken.anshar.config.IdProcessingParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class DatasetServiceTest {

    @Mock
    SubscriptionConfig subscriptionConfig;

    @Mock
    ReplicatedMap<String, SubscriptionSetup> subscriptions;

    @InjectMocks
    private DatasetService tested;

    @Test
    void test_getAllDatasetIds_whenThereIsNoConfiguration_returnsEmptyList() {
        // Arrange
        Mockito.when(subscriptionConfig.getSubscriptions()).thenReturn(List.of());
        Mockito.when(subscriptionConfig.getDiscoverySubscriptions()).thenReturn(List.of());
        Mockito.when(subscriptionConfig.getSiriApis()).thenReturn(List.of());
        Mockito.when(subscriptionConfig.getIdProcessingParameters()).thenReturn(List.of());
        Mockito.when(subscriptions.values()).thenReturn(List.of());

        // Act
        var result = tested.getAllDatasetIds();

        // Assert
        assertTrue(result.isEmpty(), "should return empty list");
    }

    @Test
    void test_getAllDatasetIds_whenThereIsConfiguration_returnsAllDatasetIds() {
        // Arrange
        SubscriptionSetup ss1 = new SubscriptionSetup();
        ss1.setDatasetId("A");
        SubscriptionSetup ss2 = new SubscriptionSetup();
        ss2.setDatasetId("B");
        DiscoverySubscription ds1 = new DiscoverySubscription();
        ds1.setDatasetId("C");
        DiscoverySubscription ds2 = new DiscoverySubscription();
        ds2.setDatasetId("D");
        SiriApi siriApi = new SiriApi();
        siriApi.setDatasetId("E");
        IdProcessingParameters ipp = new IdProcessingParameters();
        ipp.setDatasetId("F");
        Mockito.when(subscriptionConfig.getSubscriptions()).thenReturn(List.of(ss1, ss2));
        Mockito.when(subscriptionConfig.getDiscoverySubscriptions()).thenReturn(List.of(ds1, ds2));
        Mockito.when(subscriptionConfig.getSiriApis()).thenReturn(List.of(siriApi));
        Mockito.when(subscriptionConfig.getIdProcessingParameters()).thenReturn(List.of(ipp));
        Mockito.when(subscriptions.values()).thenReturn(List.of());

        // Act
        var result = tested.getAllDatasetIds();

        // Assert
        assertEquals(List.of("A", "B", "C", "D", "E", "F"), result.stream().sorted().collect(Collectors.toList()), "should return all datasetIds");

    }

    @Test
    void test_getAllDatasetIds_whenGtfsRtSubscriptionOnly_returnsItsDatasetId() {

        SubscriptionSetup gtfsRtSubscription = new SubscriptionSetup();
        gtfsRtSubscription.setDatasetId("GTFS_RT_ONLY");
        SubscriptionSetup subscriptionWithoutDataset = new SubscriptionSetup();
        Mockito.when(subscriptionConfig.getSubscriptions()).thenReturn(List.of());
        Mockito.when(subscriptionConfig.getDiscoverySubscriptions()).thenReturn(List.of());
        Mockito.when(subscriptionConfig.getSiriApis()).thenReturn(List.of());
        Mockito.when(subscriptionConfig.getIdProcessingParameters()).thenReturn(List.of());
        Mockito.when(subscriptions.values()).thenReturn(List.of(gtfsRtSubscription, subscriptionWithoutDataset));

        var result = tested.getAllDatasetIds();

        assertEquals(List.of("GTFS_RT_ONLY"), List.copyOf(result), "should return datasetId of subscriptions created on data reception");
        assertTrue(tested.exists("GTFS_RT_ONLY"), "dataset should exist");
    }

}
