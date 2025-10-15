package no.rutebanken.anshar.routes.siri.theoretical;

import no.rutebanken.anshar.config.IdProcessingParameters;
import no.rutebanken.anshar.config.ObjectType;
import no.rutebanken.anshar.integration.SpringBootBaseTest;
import no.rutebanken.anshar.subscription.SiriDataType;
import no.rutebanken.anshar.subscription.SubscriptionConfig;
import no.rutebanken.anshar.subscription.SubscriptionManager;
import org.apache.camel.CamelContext;
import org.awaitility.Durations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class TheoreticalSiriSmConsumerTest extends SpringBootBaseTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private SubscriptionConfig subscriptionConfig;

    @Autowired
    private TheoreticalSiriSmConsumer consumer;

    @Autowired
    private SubscriptionManager subscriptionManager;

    @BeforeEach
    void setUp() {
        subscriptionConfig.getIdProcessingParameters().clear();
        IdProcessingParameters vjIdProcessing = new IdProcessingParameters();
        vjIdProcessing.setInputPrefixToRemove("TEST:VehicleJourney:");
        vjIdProcessing.setDatasetId("TEST");
        vjIdProcessing.setObjectType(ObjectType.VEHICLE_JOURNEY);
        subscriptionConfig.getIdProcessingParameters().add(vjIdProcessing);
        IdProcessingParameters stopIdProcessing = new IdProcessingParameters();
        stopIdProcessing.setInputPrefixToRemove("TEST:Quay:");
        stopIdProcessing.setDatasetId("TEST");
        stopIdProcessing.setObjectType(ObjectType.STOP);
        subscriptionConfig.getIdProcessingParameters().add(stopIdProcessing);
        IdProcessingParameters lineIdProcessing = new IdProcessingParameters();
        lineIdProcessing.setInputPrefixToRemove("TEST:Line:");
        lineIdProcessing.setDatasetId("TEST");
        lineIdProcessing.setObjectType(ObjectType.LINE);
        subscriptionConfig.getIdProcessingParameters().add(lineIdProcessing);
        subscriptionManager.clearAllSubscriptions();
    }

    @Test
    void readDataAndProduceSiriTest() {
        camelContext.start();

        consumer.ingestSiriSmData();

        await()
                .atMost(Durations.TEN_SECONDS)
                .with()
                .pollInterval(Durations.ONE_SECOND)
                .until(() -> subscriptionManager.getAllSubscriptions(SiriDataType.STOP_MONITORING).size() == 20);

        assertThat(subscriptionManager.getAllSubscriptions(SiriDataType.STOP_MONITORING))
                .extracting("datasetId").containsOnly("TEST");
        assertThat(subscriptionManager.getAllSubscriptions(SiriDataType.STOP_MONITORING))
                .flatExtracting("stopMonitoringRefValues").contains(
                        "TEST:Quay:44708",
                        "TEST:Quay:44089",
                        "TEST:Quay:44157",
                        "TEST:Quay:44063",
                        "TEST:Quay:44034",
                        "TEST:Quay:44082",
                        "TEST:Quay:44158",
                        "TEST:Quay:44005",
                        "TEST:Quay:44505",
                        "TEST:Quay:44087",
                        "TEST:Quay:44205",
                        "TEST:Quay:44127",
                        "TEST:Quay:44068",
                        "TEST:Quay:44013",
                        "TEST:Quay:44004",
                        "TEST:Quay:44036",
                        "TEST:Quay:44032",
                        "TEST:Quay:44802",
                        "TEST:Quay:44616",
                        "TEST:Quay:44054");
    }

}