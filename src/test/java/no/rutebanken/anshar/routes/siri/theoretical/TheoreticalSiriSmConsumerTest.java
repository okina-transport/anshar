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
                .flatExtracting("stopMonitoringRefValues").containsExactly(
                        "44708",
                        "44089",
                        "44157",
                        "44063",
                        "44034",
                        "44082",
                        "44158",
                        "44005",
                        "44505",
                        "44087",
                        "44205",
                        "44127",
                        "44068",
                        "44013",
                        "44004",
                        "44036",
                        "44032",
                        "44802",
                        "44616",
                        "44054");
    }

}